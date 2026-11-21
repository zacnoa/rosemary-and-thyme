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
import java.util.UUID

/**
 * Consumes a verification code, confirms the email, and creates a session in one transaction.
 * Other verification records are invalidated; the route sets the cookie only after commit.
 *
 * @param verificationId identifier returned when the registration started
 * @param code raw code sent to the account's email address
 * @return the new session for the verified account
 * @throws BadRequestException if the code is invalid, expired, used, or the email is already verified
 */
fun AuthService.confirmEmailVerification(verificationId: String, code: String): UserSession {
    val recordId = runCatching { UUID.fromString(verificationId) }.getOrNull()
        ?: throw invalidVerificationCode()

    return Service.dsl.transactionResult { configuration ->
        val tx = DSL.using(configuration)
        val verification = tx.select(EMAIL_VERIFICATION_TOKENS.USER_ID, EMAIL_VERIFICATION_TOKENS.TOKEN_HASH)
            .from(EMAIL_VERIFICATION_TOKENS)
            .where(EMAIL_VERIFICATION_TOKENS.ID.eq(recordId))
            .and(EMAIL_VERIFICATION_TOKENS.USED_AT.isNull)
            .and(EMAIL_VERIFICATION_TOKENS.EXPIRES_AT.gt(OffsetDateTime.now()))
            .fetchOne() ?: throw invalidVerificationCode()

        val userId = checkNotNull(verification.get(EMAIL_VERIFICATION_TOKENS.USER_ID))
        val tokenHash = CryptoService.sha256("$userId:$code")
        if (verification.get(EMAIL_VERIFICATION_TOKENS.TOKEN_HASH) != tokenHash) {
            throw invalidVerificationCode()
        }

        val user = tx.selectFrom(USERS)
            .where(USERS.ID.eq(userId))
            .forUpdate()
            .fetchOne() ?: throw invalidVerificationCode()
        if (user.emailVerified == true) throw invalidVerificationCode()

        val now = OffsetDateTime.now()
        tx.update(EMAIL_VERIFICATION_TOKENS)
            .set(EMAIL_VERIFICATION_TOKENS.USED_AT, now)
            .where(EMAIL_VERIFICATION_TOKENS.ID.eq(recordId))
            .and(EMAIL_VERIFICATION_TOKENS.TOKEN_HASH.eq(tokenHash))
            .and(EMAIL_VERIFICATION_TOKENS.USED_AT.isNull)
            .and(EMAIL_VERIFICATION_TOKENS.EXPIRES_AT.gt(now))
            .returning(EMAIL_VERIFICATION_TOKENS.USER_ID)
            .fetchOne() ?: throw invalidVerificationCode()

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

/** Returns the same error for all rejected verification codes. */
private fun invalidVerificationCode() = BadRequestException("The email verification code is invalid or expired")
