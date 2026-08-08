package rosemary_and_thyme.backend.services.auth

import rosemary_and_thyme.backend.exceptions.EmailNotVerifiedException
import rosemary_and_thyme.backend.exceptions.TooManyRequestsException
import rosemary_and_thyme.backend.exceptions.UserCredentialsException
import org.springframework.stereotype.Service
import org.springframework.security.crypto.bcrypt.BCrypt
import rosemary_and_thyme.backend.jooqgenerated.auth.tables.records.UsersRecord
import rosemary_and_thyme.backend.models.RegisterRequest
import rosemary_and_thyme.backend.repositories.auth.UserRepository
import rosemary_and_thyme.backend.repositories.dbNotNull
import rosemary_and_thyme.backend.services.ratelimit.RateLimiterService
import io.github.bucket4j.Bandwidth
import java.time.Duration
import java.util.UUID

@Service
class AuthService(
    private val userRepository: UserRepository,
    val sessionService: SessionService,
    private val emailVerificationService: EmailVerificationService,
    private val rateLimiter: RateLimiterService
) {

    private val loginLimit = Bandwidth.builder().capacity(5).refillIntervally(5, Duration.ofMinutes(15)).build()


    fun validateSession(sessionToken: String): UsersRecord =

        sessionService.validateSession(sessionToken)


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
     * No longer logs the user in directly - the account exists but stays unusable
     * until verifyEmail() confirms the address, so a session is only issued there.
     */
    fun register(registerRequest: RegisterRequest) {

        val passwordHash = BCrypt.hashpw(registerRequest.password, BCrypt.gensalt())
        val user = userRepository.insertUser(registerRequest.email, passwordHash, registerRequest.username)

        emailVerificationService.issueVerificationToken(user.id.dbNotNull("id"), user.email.dbNotNull("email"))
    }

    /**
     * @return a fresh session token so the user lands logged-in right after verifying
     */
    fun verifyEmail(token: String): String {
        val userId: UUID = emailVerificationService.verify(token)
        return sessionService.createSession(userId)
    }

    fun resendVerificationEmail(email: String) {
        emailVerificationService.resend(email)
    }

    fun logout(sessionToken: String) {
        sessionService.revokeSession(sessionToken)
    }

}