package rosemary_and_thyme.backend.services.auth

import rosemary_and_thyme.backend.exceptions.EmailNotVerifiedException
import rosemary_and_thyme.backend.exceptions.GoogleAccountException
import rosemary_and_thyme.backend.exceptions.TooManyRequestsException
import rosemary_and_thyme.backend.exceptions.UserCredentialsException
import org.springframework.stereotype.Service
import org.springframework.security.crypto.bcrypt.BCrypt
import rosemary_and_thyme.backend.jooqgenerated.app_auth.tables.records.UsersRecord
import rosemary_and_thyme.backend.models.auth.RegisterRequest
import rosemary_and_thyme.backend.repositories.auth.UserRepository
import rosemary_and_thyme.backend.repositories.dbNotNull
import rosemary_and_thyme.backend.services.ratelimit.RateLimiterService
import io.github.bucket4j.Bandwidth
import java.time.Duration
import java.util.UUID

/**
 * Top-level auth use cases, orchestrating [UserRepository], [SessionService],
 * [EmailVerificationService] and [PasswordResetService]. This is the layer
 * [rosemary_and_thyme.backend.controllers.AuthController]/[rosemary_and_thyme.backend.controllers.UserController]
 * talk to - it doesn't touch jOOQ or cookies directly itself.
 */
@Service
class AuthService(
    private val userRepository: UserRepository,
    val sessionService: SessionService,
    private val emailVerificationService: EmailVerificationService,
    private val passwordResetService: PasswordResetService,
    private val rateLimiter: RateLimiterService
) {

    // 5 attempts per email per 15 minutes - independent of RateLimitFilter's coarser,
    // IP-keyed limit on the whole /auth path
    private val loginLimit = Bandwidth.builder().capacity(5).refillIntervally(5, Duration.ofMinutes(15)).build()

    // 5 attempts per user per 15 minutes - same shape as loginLimit, guards against
    // brute-forcing the current password on POST /user/password
    private val changePasswordLimit = Bandwidth.builder().capacity(5).refillIntervally(5, Duration.ofMinutes(15)).build()


    /**
     * @param sessionToken the raw session token from the client's cookie
     * @return the session's owner
     * @throws rosemary_and_thyme.backend.exceptions.SessionException if the session is
     * missing, revoked, or expired - see [SessionService.validateSession]
     */
    fun validateSession(sessionToken: String): UsersRecord =

        sessionService.validateSession(sessionToken)


    /**
     * Authenticates a user by email/password and starts a new session for them.
     *
     * @param email the account's email
     * @param password the account's plaintext password, checked against the stored BCrypt hash
     * @return a raw session token for the new session
     * @throws TooManyRequestsException if this email has exceeded [loginLimit]
     * @throws UserCredentialsException if the email is unknown or the password
     * doesn't match (the same generic message is used for both, so failed attempts
     * can't be used to enumerate which emails are registered)
     * @throws GoogleAccountException if the account exists but has no password to
     * check against - a Google-only account (see
     * [rosemary_and_thyme.backend.repositories.auth.UserRepository.insertOauthUser]).
     * Deliberately distinct from [UserCredentialsException]: `routes/auth/login.tsx`
     * catches this specifically and redirects straight into the Google sign-in flow
     * instead of showing a credentials error, regardless of what was typed in the
     * password field - this does mean an emailed-registered-via-Google account is now
     * distinguishable from a wrong-password attempt, a deliberate trade-off for that
     * UX (see the class-level feature request this implements).
     * @throws EmailNotVerifiedException if the account exists but its email isn't verified yet
     */
    fun login(email: String, password: String): String {

        if (!rateLimiter.tryConsume("login", email, loginLimit)) {
            throw TooManyRequestsException("Too many login attempts, please try again later")
        }

        val user =
            userRepository.selectUserByEmail(email) ?: throw UserCredentialsException("Email or password not correct")

        // captured to a local val rather than smart-cast off `user.password == null` -
        // it's a mutable `var` on the jOOQ record, so Kotlin won't smart-cast it
        // across the `||` even within the same expression
        val passwordHash = user.password
        if (passwordHash == null) {
            throw GoogleAccountException("This account signs in with Google")
        }
        if (!BCrypt.checkpw(password, passwordHash)) {
            throw UserCredentialsException("Email or password not correct")
        }

        if (user.emailVerified != true) {
            throw EmailNotVerifiedException("Please verify your email before logging in")
        }

        val sessionToken = sessionService.createSession(user.id.dbNotNull("id"))

        return sessionToken
    }

    /**
     * Creates a new, unverified account and sends it a verification email.
     *
     * No longer logs the user in directly - the account exists but stays unusable
     * until verifyEmail() confirms the address, so a session is only issued there.
     *
     * @param registerRequest the new account's username/email/password
     * @throws rosemary_and_thyme.backend.exceptions.UserAlreadyExistsException if the
     * email is already registered
     */
    fun register(registerRequest: RegisterRequest) {

        val passwordHash = BCrypt.hashpw(registerRequest.password, BCrypt.gensalt())
        val user = userRepository.insertUser(registerRequest.email, passwordHash, registerRequest.username)

        emailVerificationService.issueVerificationToken(user.id.dbNotNull("id"), user.email.dbNotNull("email"))
    }

    /**
     * Confirms an account's email from its verification token, then immediately logs
     * the user in.
     *
     * @param token the raw verification token from the emailed link
     * @return a fresh session token so the user lands logged-in right after verifying
     * @throws rosemary_and_thyme.backend.exceptions.InvalidTokenException if the token
     * is unknown, already used, or expired - see [EmailVerificationService.verify]
     */
    fun verifyEmail(token: String): String {
        val userId: UUID = emailVerificationService.verify(token)
        return sessionService.createSession(userId)
    }

    /**
     * Re-sends a verification email. Delegates entirely to
     * [EmailVerificationService.resend] - see there for the rate-limiting and
     * email-enumeration-avoidance behavior.
     *
     * @param email the email to resend a verification link to
     */
    fun resendVerificationEmail(email: String) {
        emailVerificationService.resend(email)
    }

    /**
     * @param sessionToken the raw session token to revoke (see [SessionService.revokeSession])
     */
    fun logout(sessionToken: String) {
        sessionService.revokeSession(sessionToken)
    }

    /**
     * Changes an already-logged-in user's password: checks [currentPassword] against
     * the stored hash, then replaces it and rotates every session for the account
     * (see [SessionService.revokeAllSessions]) - including the one that made this very
     * request - issuing a fresh one so the caller stays logged in while any other
     * session elsewhere is cut off. Rate-limited per user (not per email, since the
     * caller is already authenticated) to slow down brute-forcing [currentPassword].
     *
     * @param user the authenticated caller
     * @param currentPassword checked against the stored BCrypt hash
     * @param newPassword the new plaintext password to hash and store
     * @return a fresh raw session token for the request that just changed the password
     * @throws TooManyRequestsException if this account has exceeded [changePasswordLimit]
     * @throws GoogleAccountException if the account has no password to check against
     * (a Google-only account)
     * @throws UserCredentialsException if [currentPassword] doesn't match
     */
    fun changePassword(user: UsersRecord, currentPassword: String, newPassword: String): String {
        val userId = user.id.dbNotNull("id")

        if (!rateLimiter.tryConsume("change-password", userId.toString(), changePasswordLimit)) {
            throw TooManyRequestsException("Too many attempts, please try again later")
        }

        val passwordHash = user.password
            ?: throw GoogleAccountException("This account signs in with Google and has no password to change")

        if (!BCrypt.checkpw(currentPassword, passwordHash)) {
            throw UserCredentialsException("Current password is not correct")
        }

        userRepository.updatePassword(userId, BCrypt.hashpw(newPassword, BCrypt.gensalt()))
        sessionService.revokeAllSessions(userId)

        return sessionService.createSession(userId)
    }

    /**
     * Starts the "forgot password" flow. Delegates entirely to
     * [PasswordResetService.requestReset] - see there for the rate-limiting and
     * email-enumeration-avoidance behavior (including staying silent for a
     * Google-only account).
     *
     * @param email the email to send a reset link to
     */
    fun requestPasswordReset(email: String) {
        passwordResetService.requestReset(email)
    }

    /**
     * Confirms a password reset from its emailed token, then rotates every session
     * for the account (same reasoning as [changePassword]) and logs the caller in
     * with a fresh one.
     *
     * @param token the raw reset token from the emailed link
     * @param newPassword the new plaintext password to hash and store
     * @return a fresh raw session token so the user lands logged-in right after resetting
     * @throws rosemary_and_thyme.backend.exceptions.InvalidTokenException if the token
     * is unknown, already used, or expired - see [PasswordResetService.confirmReset]
     */
    fun resetPassword(token: String, newPassword: String): String {
        val userId = passwordResetService.confirmReset(token, newPassword)
        sessionService.revokeAllSessions(userId)
        return sessionService.createSession(userId)
    }

}
