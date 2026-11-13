package rosemary_and_thyme.services.auth_service

import io.ktor.server.plugins.BadRequestException
import org.jooq.impl.DSL
import rosemary_and_thyme.jooq.app_auth.tables.references.EMAIL_VERIFICATION_TOKENS
import rosemary_and_thyme.jooq.app_auth.tables.references.USERS
import rosemary_and_thyme.models.auth.UserSession
import rosemary_and_thyme.services.AuthService
import rosemary_and_thyme.services.CryptoService
import rosemary_and_thyme.services.Service
import java.time.OffsetDateTime

/**
 * Consumes a verification token, confirms the email, and creates a session in one transaction.
 * Other verification tokens are invalidated; the route sets the cookie only after commit.
 *
 * @param token raw token delivered through the verification link
 * @return the new session for the verified account
 * @throws BadRequestException if the token is invalid, expired, used, or the email is already verified
 */
fun AuthService.confirmEmailVerification(token: String): UserSession {
    if (!Regex("^[A-Za-z0-9_-]{43}$").matches(token)) throw invalidVerificationToken()
    val tokenHash = CryptoService.sha256(token)

    return Service.dsl.transactionResult { configuration ->
        val tx = DSL.using(configuration)
        val userId = tx.select(EMAIL_VERIFICATION_TOKENS.USER_ID)
            .from(EMAIL_VERIFICATION_TOKENS)
            .where(EMAIL_VERIFICATION_TOKENS.TOKEN_HASH.eq(tokenHash))
            .fetchOne()?.value1() ?: throw invalidVerificationToken()

        val user = tx.selectFrom(USERS)
            .where(USERS.ID.eq(userId))
            .forUpdate()
            .fetchOne() ?: throw invalidVerificationToken()
        if (user.emailVerified == true) throw invalidVerificationToken()

        val now = OffsetDateTime.now()
        tx.update(EMAIL_VERIFICATION_TOKENS)
            .set(EMAIL_VERIFICATION_TOKENS.USED_AT, now)
            .where(EMAIL_VERIFICATION_TOKENS.TOKEN_HASH.eq(tokenHash))
            .and(EMAIL_VERIFICATION_TOKENS.USED_AT.isNull)
            .and(EMAIL_VERIFICATION_TOKENS.EXPIRES_AT.gt(now))
            .returning(EMAIL_VERIFICATION_TOKENS.USER_ID)
            .fetchOne() ?: throw invalidVerificationToken()

        check(tx.update(USERS)
            .set(USERS.EMAIL_VERIFIED, true)
            .where(USERS.ID.eq(userId))
            .execute() == 1) { "The email address could not be verified" }

        tx.update(EMAIL_VERIFICATION_TOKENS)
            .set(EMAIL_VERIFICATION_TOKENS.USED_AT, now)
            .where(EMAIL_VERIFICATION_TOKENS.USER_ID.eq(userId))
            .and(EMAIL_VERIFICATION_TOKENS.USED_AT.isNull)
            .execute()

        Service.authService.createSession(userId, tx)
    }
}

/** Returns the same error for all rejected verification tokens. */
private fun invalidVerificationToken() = BadRequestException("The email verification token is invalid or expired")
