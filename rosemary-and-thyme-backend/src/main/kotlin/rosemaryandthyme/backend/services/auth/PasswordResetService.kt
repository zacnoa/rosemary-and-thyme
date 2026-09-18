package rosemaryandthyme.backend.services.auth

import io.github.bucket4j.Bandwidth
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.crypto.bcrypt.BCrypt
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rosemaryandthyme.backend.exceptions.InvalidTokenException
import rosemaryandthyme.backend.exceptions.TooManyRequestsException
import rosemaryandthyme.backend.repositories.auth.PasswordResetRepository
import rosemaryandthyme.backend.repositories.auth.UserRepository
import rosemaryandthyme.backend.repositories.dbNotNull
import rosemaryandthyme.backend.services.email.ResendEmailService
import rosemaryandthyme.backend.services.ratelimit.RateLimiterService
import java.time.Duration
import java.util.UUID

/** Issues and consumes password-reset tokens for password accounts. */
@Service
class PasswordResetService(
    private val tokenRepository: PasswordResetRepository,
    private val userRepository: UserRepository,
    private val emailService: ResendEmailService,
    private val rateLimiter: RateLimiterService,
    @Value($$"${app.frontend-url}") private val frontendUrl: String
) {

    // 1/minute and 5/day per email - same shape as EmailVerificationService's resend
    // limits, independent of RateLimitFilter's coarser, IP-keyed limit on /auth
    private val requestPerMinuteLimit = Bandwidth.builder().capacity(1).refillIntervally(1, Duration.ofMinutes(1)).build()
    private val requestPerDayLimit = Bandwidth.builder().capacity(5).refillIntervally(5, Duration.ofDays(1)).build()

    /** Sends a password-reset email when the account supports passwords.
     * @param email the requested account email.
     * @throws TooManyRequestsException if the email exceeded its request limit.
     */
    fun requestReset(email: String) {
        // checked before the DB lookup so it doesn't leak whether the email is
        // registered, and so it also caps unbounded lookups for unknown emails
        if (!rateLimiter.tryConsume("password-reset", email, requestPerMinuteLimit, requestPerDayLimit)) {
            throw TooManyRequestsException("Too many password reset emails requested, please try again later")
        }

        val user = userRepository.selectUserByEmail(email) ?: return
        // Google-only account - see this class's own KDoc for why this stays silent
        if (user.password == null) return

        val userId = user.id.dbNotNull("id")
        tokenRepository.updateTokensUsedByUserId(userId)

        val rawToken = generateSessionToken()
        val hashedToken = hashToken(rawToken)
        tokenRepository.insertToken(userId, hashedToken)

        val resetLink = "$frontendUrl/auth/reset-password?token=$rawToken"
        emailService.sendPasswordResetEmail(email, resetLink)
    }

    /** Replaces the password associated with a reset token.
     * @param rawToken the raw token from the reset link.
     * @param newPassword the replacement plaintext password.
     * @return the user whose password was changed.
     * @throws InvalidTokenException if the token is invalid, used, or expired.
     */
    @Transactional
    fun confirmReset(rawToken: String, newPassword: String): UUID {
        val hashedToken = hashToken(rawToken)

        val token = tokenRepository.selectValidToken(hashedToken)
            ?: throw InvalidTokenException("This password reset link is invalid or has expired")

        tokenRepository.updateTokenUsedAt(token.id.dbNotNull("id"))

        val passwordHash = BCrypt.hashpw(newPassword, BCrypt.gensalt())
        userRepository.updatePassword(token.userId, passwordHash)

        return token.userId
    }
}
