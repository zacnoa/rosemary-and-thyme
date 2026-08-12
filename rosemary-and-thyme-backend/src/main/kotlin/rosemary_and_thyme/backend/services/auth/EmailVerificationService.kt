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

/**
 * Owns the "confirm this email address belongs to you" workflow that runs between
 * registration and first login:
 *
 * 1. [rosemary_and_thyme.backend.services.auth.AuthService.register] creates the
 *    account, then calls [issueVerificationToken] here, which mints a token, stores
 *    only its hash, and asks [ResendEmailService] to email the user a link containing
 *    the raw token (`{frontendUrl}/auth/verify-email?token={rawToken}`). The email's
 *    actual subject/HTML body is built by [ResendEmailService.sendVerificationEmail] -
 *    this class only ever supplies it the recipient and the link.
 * 2. The user clicks the link; the frontend posts the token to
 *    `POST /auth/verify-email`, which reaches [verify] here. The row is looked up by
 *    the token's hash (never the raw token itself, per the hashing rationale in
 *    [SessionService]), marked used, and the account is flipped to `email_verified = true`.
 * 3. If the link is lost or expires (24h TTL, enforced in
 *    [EmailVerificationRepository.selectValidToken]), [resend] is called from
 *    `POST /auth/verify-email/resend`, which invalidates any still-outstanding token
 *    for the account and starts over from step 1.
 *
 * Uses the exact same random-token/hash scheme as [SessionService] - see
 * [generateSessionToken]/[hashToken] there - just against a different table
 * (`app_auth.email_verification_tokens` vs `app_auth.sessions`).
 */
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

    /**
     * Generates a new raw token, stores its hash, and emails the raw token as a link.
     * Reuses the same random-token/hash scheme SessionService uses for session tokens -
     * only the hash ever touches the database.
     *
     * @param userId the account the token verifies
     * @param email the address to send the verification link to
     */
    fun issueVerificationToken(userId: UUID, email: String) {
        val rawToken = generateSessionToken()
        val hashedToken = hashToken(rawToken)

        tokenRepository.insertToken(userId, hashedToken)

        val verificationLink = "$frontendUrl/auth/verify-email?token=$rawToken"
        emailService.sendVerificationEmail(email, verificationLink)
    }

    /**
     * Consumes a verification token: marks it used and flips the owning account to
     * verified, in one transaction so a crash between the two updates can't leave the
     * token burned but the account still unverified.
     *
     * @param rawToken the raw verification token from the emailed link
     * @return the id of the user the token belonged to
     * @throws InvalidTokenException if the token's hash matches no row, or matches a
     * row that is already used or expired (see [EmailVerificationRepository.selectValidToken])
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

    /**
     * Re-issues a verification email for an account whose link expired or was lost.
     *
     * Silently no-ops for unknown/already-verified emails so this endpoint can't be used
     * to probe which emails are registered.
     *
     * @param email the account's email to resend a verification link to
     * @throws TooManyRequestsException if this email has exceeded [resendPerMinuteLimit]
     * or [resendPerDayLimit]
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
