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

/** Issues and consumes email-verification tokens for password accounts. */
@Service
class EmailVerificationService(
    private val tokenRepository: EmailVerificationRepository,
    private val userRepository: UserRepository,
    private val emailService: ResendEmailService,
    private val rateLimiter: RateLimiterService,
    @Value($$"${app.frontend-url}") private val frontendUrl: String
) {

    // 1/minute and 5/day per email - independent of RateLimitFilter's coarser,
    // IP-keyed limit on the whole /auth path
    private val resendPerMinuteLimit = Bandwidth.builder().capacity(1).refillIntervally(1, Duration.ofMinutes(1)).build()
    private val resendPerDayLimit = Bandwidth.builder().capacity(5).refillIntervally(5, Duration.ofDays(1)).build()

    /** Issues a verification token and emails its link.
     * @param userId the account awaiting verification.
     * @param email the destination address.
     */
    fun issueVerificationToken(userId: UUID, email: String) {
        val rawToken = generateSessionToken()
        val hashedToken = hashToken(rawToken)

        tokenRepository.insertToken(userId, hashedToken)

        val verificationLink = "$frontendUrl/auth/verify-email?token=$rawToken"
        emailService.sendVerificationEmail(email, verificationLink)
    }

    /** Verifies the account that owns a token.
     * @param rawToken the raw token from the verification link.
     * @return the verified user id.
     * @throws InvalidTokenException if the token is invalid, used, or expired.
     */
    @Transactional
    fun verify(rawToken: String): UUID {
        val hashedToken = hashToken(rawToken)

        val token = tokenRepository.selectValidToken(hashedToken)
            ?: throw InvalidTokenException("This verification link is invalid or has expired")

        tokenRepository.updateTokenUsedAt(token.id.dbNotNull("id"))
        userRepository.updateEmailVerified(token.userId)

        return token.userId
    }

    /** Sends a replacement verification email when the account needs one.
     * @param email the requested account email.
     * @throws TooManyRequestsException if the email exceeded its resend limit.
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
        tokenRepository.updateTokensUsedByUserId(userId)
        issueVerificationToken(userId, email)
    }
}
