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

/** Coordinates password authentication and account-management workflows. */
@Service
class AuthService(
    private val userRepository: UserRepository,
    val sessionService: SessionService,
    private val emailVerificationService: EmailVerificationService,
    private val passwordResetService: PasswordResetService,
    private val rateLimiter: RateLimiterService
) {

    // 10 attempts per email per 15 minutes - independent of RateLimitFilter's coarser,
    // IP-keyed limit on the whole /auth path
    private val loginLimit = Bandwidth.builder().capacity(10).refillIntervally(10, Duration.ofMinutes(15)).build()

    // 5 attempts per user per 15 minutes - same shape as loginLimit, guards against
    // brute-forcing the current password on POST /user/password
    private val changePasswordLimit = Bandwidth.builder().capacity(5).refillIntervally(5, Duration.ofMinutes(15)).build()


    /** Validates a session token through the session service.
     * @param sessionToken the raw session token.
     * @return the authenticated user.
     * @throws rosemary_and_thyme.backend.exceptions.SessionException if the session is invalid.
     */
    fun validateSession(sessionToken: String): UsersRecord =

        sessionService.validateSession(sessionToken)


    /** Authenticates a verified password-based account.
     * @param email the account email.
     * @param password the submitted plaintext password.
     * @return a new raw session token.
     * @throws UserCredentialsException if the credentials are invalid.
     * @throws EmailNotVerifiedException if the account email is unverified.
     * @throws GoogleAccountException if the account has no password.
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

    /** Registers an unverified password-based account.
     * @param registerRequest the username, email, and plaintext password.
     * @throws rosemary_and_thyme.backend.exceptions.UserAlreadyExistsException if the email is registered.
     */
    fun register(registerRequest: RegisterRequest) {

        val passwordHash = BCrypt.hashpw(registerRequest.password, BCrypt.gensalt())
        val user = userRepository.insertUser(registerRequest.email, passwordHash, registerRequest.username)

        emailVerificationService.issueVerificationToken(user.id.dbNotNull("id"), user.email.dbNotNull("email"))
    }

    /** Verifies an account email and creates a session.
     * @param token the raw verification token.
     * @return a new raw session token.
     * @throws rosemary_and_thyme.backend.exceptions.InvalidTokenException if the token is invalid.
     */
    fun verifyEmail(token: String): String {
        val userId: UUID = emailVerificationService.verify(token)
        return sessionService.createSession(userId)
    }

    /** Sends a new verification email when applicable.
     * @param email the account email.
     * @throws TooManyRequestsException if the email exceeded its resend limit.
     */
    fun resendVerificationEmail(email: String) {
        emailVerificationService.resend(email)
    }

    /** Logs a user out by revoking their session.
     * @param sessionToken the raw session token.
     */
    fun logout(sessionToken: String) {
        sessionService.revokeSession(sessionToken)
    }

    /** Changes a user's password and rotates all of their sessions.
     * @param user the authenticated user changing their password.
     * @param currentPassword the current plaintext password.
     * @param newPassword the replacement plaintext password.
     * @return a fresh session token for the caller.
     * @throws UserCredentialsException if the current password is incorrect.
     * @throws GoogleAccountException if the account has no password.
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

    /** Starts the password-reset flow.
     * @param email the account email.
     * @throws TooManyRequestsException if the email exceeded its reset-request limit.
     */
    fun requestPasswordReset(email: String) {
        passwordResetService.requestReset(email)
    }

    /** Resets a password and rotates every existing session.
     * @param token the raw reset token.
     * @param newPassword the replacement plaintext password.
     * @return a fresh session token.
     * @throws rosemary_and_thyme.backend.exceptions.InvalidTokenException if the token is invalid.
     */
    fun resetPassword(token: String, newPassword: String): String {
        val userId = passwordResetService.confirmReset(token, newPassword)
        sessionService.revokeAllSessions(userId)
        return sessionService.createSession(userId)
    }

}
