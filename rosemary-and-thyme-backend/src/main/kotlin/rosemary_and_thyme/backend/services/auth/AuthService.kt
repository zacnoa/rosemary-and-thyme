package rosemary_and_thyme.backend.services.auth

import rosemary_and_thyme.backend.exceptions.EmailNotVerifiedException
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
 * Top-level auth use cases, orchestrating [UserRepository], [SessionService] and
 * [EmailVerificationService]. This is the layer [rosemary_and_thyme.backend.controllers.AuthController]
 * talks to - it doesn't touch jOOQ or cookies directly itself.
 */
@Service
class AuthService(
    private val userRepository: UserRepository,
    val sessionService: SessionService,
    private val emailVerificationService: EmailVerificationService,
    private val rateLimiter: RateLimiterService
) {

    // 5 attempts per email per 15 minutes - independent of RateLimitFilter's coarser,
    // IP-keyed limit on the whole /auth path
    private val loginLimit = Bandwidth.builder().capacity(5).refillIntervally(5, Duration.ofMinutes(15)).build()


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
     * @throws UserCredentialsException if the email is unknown or the password doesn't match
     * (the same generic message is used for both cases, so failed attempts can't be used
     * to enumerate which emails are registered)
     * @throws EmailNotVerifiedException if the account exists but its email isn't verified yet
     */
    fun login(email: String, password: String): String {

        if (!rateLimiter.tryConsume("login", email, loginLimit)) {
            throw TooManyRequestsException("Too many login attempts, please try again later")
        }

        val user =
            userRepository.selectUserByEmail(email) ?: throw UserCredentialsException("Email or password not correct")


        if (!BCrypt.checkpw(password, user.password)) {
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

}
