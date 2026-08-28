package rosemary_and_thyme.backend.services.auth

import io.github.bucket4j.Bandwidth
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.crypto.bcrypt.BCrypt
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rosemary_and_thyme.backend.exceptions.InvalidTokenException
import rosemary_and_thyme.backend.exceptions.TooManyRequestsException
import rosemary_and_thyme.backend.repositories.auth.PasswordResetRepository
import rosemary_and_thyme.backend.repositories.auth.UserRepository
import rosemary_and_thyme.backend.repositories.dbNotNull
import rosemary_and_thyme.backend.services.email.ResendEmailService
import rosemary_and_thyme.backend.services.ratelimit.RateLimiterService
import java.time.Duration
import java.util.UUID

/**
 * Owns the "forgot password" workflow - structurally identical to
 * [EmailVerificationService], just against `app_auth.password_reset_tokens` instead
 * of `app_auth.email_verification_tokens`, and confirming replaces a password
 * instead of flipping a verified flag:
 *
 * 1. [requestReset] mints a token, stores only its hash, and emails the raw token as
 *    a link (`{frontendUrl}/auth/reset-password?token={rawToken}`). Silently no-ops
 *    for an unknown email or a Google-only account (`users.password` is `null` -
 *    there's nothing to reset, and a Google user should use "Continue with Google"
 *    instead) - both cases stay indistinguishable from "email sent" to the caller,
 *    same email-enumeration reasoning as [EmailVerificationService.resend].
 * 2. The user clicks the link; the frontend posts the token + new password to
 *    `POST /auth/password-reset/confirm`, which reaches [confirmReset]. The row is
 *    looked up by the token's hash, marked used, and the account's password is
 *    replaced. [rosemary_and_thyme.backend.services.auth.AuthService.resetPassword]
 *    then revokes every existing session for the account (see
 *    [SessionService.revokeAllSessions]) and issues a fresh one, so the request that
 *    just confirmed the reset lands logged in while any other, possibly-compromised
 *    session elsewhere is cut off.
 *
 * Uses the exact same random-token/hash scheme as [SessionService] - see
 * [generateSessionToken]/[hashToken] there.
 */
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

    /**
     * Re-issues a reset email for an account whose link expired or was lost -
     * invalidates any still-outstanding token for the account first (same
     * "only the newest link works" behavior as [EmailVerificationService.resend]).
     *
     * @param email the account's email to send a reset link to
     * @throws TooManyRequestsException if this email has exceeded [requestPerMinuteLimit]
     * or [requestPerDayLimit]
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

    /**
     * Consumes a reset token: marks it used and replaces the account's password, in
     * one transaction so a crash partway through can't leave the token burned but the
     * password unchanged (or vice versa). Doesn't touch sessions itself - see
     * [rosemary_and_thyme.backend.services.auth.AuthService.resetPassword], the only
     * caller, which revokes every existing session and issues a fresh one right after
     * this returns.
     *
     * @param rawToken the raw reset token from the emailed link
     * @param newPassword the new plaintext password to hash and store
     * @return the id of the user whose password was reset
     * @throws InvalidTokenException if the token's hash matches no row, or matches a
     * row that is already used or expired
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
