package rosemary_and_thyme.backend.services.auth

import io.github.bucket4j.Bandwidth
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rosemary_and_thyme.backend.exceptions.InvalidTokenException
import rosemary_and_thyme.backend.exceptions.TooManyRequestsException
import rosemary_and_thyme.backend.repositories.auth.EmailVerificationRepository
import rosemary_and_thyme.backend.repositories.auth.UserRepository
import rosemary_and_thyme.backend.repositories.dbNotNull
import rosemary_and_thyme.backend.services.email.ResendEmailService
import rosemary_and_thyme.backend.services.ratelimit.RateLimiterService
import java.time.Duration
import java.util.UUID

@Service
class EmailVerificationService(
    private val tokenRepository: EmailVerificationRepository,
    private val userRepository: UserRepository,
    private val emailService: ResendEmailService,
    private val rateLimiter: RateLimiterService,
    @Value($$"${app.frontend-url}") private val frontendUrl: String
) {

    private val resendPerMinuteLimit = Bandwidth.builder().capacity(1).refillIntervally(1, Duration.ofMinutes(1)).build()
    private val resendPerDayLimit = Bandwidth.builder().capacity(5).refillIntervally(5, Duration.ofDays(1)).build()

    /**
     * Generates a new raw token, stores its hash, and emails the raw token as a link.
     * Reuses the same random-token/hash scheme SessionService uses for session tokens -
     * only the hash ever touches the database.
     */
    fun issueVerificationToken(userId: UUID, email: String) {
        val rawToken = generateSessionToken()
        val hashedToken = hashToken(rawToken)

        tokenRepository.insertToken(userId, hashedToken)

        val verificationLink = "$frontendUrl/auth/verify-email?token=$rawToken"
        emailService.sendVerificationEmail(email, verificationLink)
    }

    /**
     * @return the id of the user the token belonged to
     */
    @Transactional
    fun verify(rawToken: String): UUID {
        val hashedToken = hashToken(rawToken)

        val token = tokenRepository.fetchValidToken(hashedToken)
            ?: throw InvalidTokenException("This verification link is invalid or has expired")

        tokenRepository.markUsed(token.id.dbNotNull("id"))
        userRepository.markEmailVerified(token.userId)

        return token.userId
    }

    /**
     * Silently no-ops for unknown/already-verified emails so this endpoint can't be used
     * to probe which emails are registered.
     */
    fun resend(email: String) {
        // checked before the DB lookup so it doesn't leak whether the email is
        // registered, and so it also caps unbounded lookups for unknown emails
        if (!rateLimiter.tryConsume("resend", email, resendPerMinuteLimit, resendPerDayLimit)) {
            throw TooManyRequestsException("Too many verification emails requested, please try again later")
        }

        val user = userRepository.selectUserByEmail(email) ?: return
        if (user.emailVerified == true) return

        val userId = user.id.dbNotNull("id")
        tokenRepository.invalidateAllForUser(userId)
        issueVerificationToken(userId, email)
    }
}
