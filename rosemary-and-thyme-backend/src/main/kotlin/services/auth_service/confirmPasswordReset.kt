package rosemary_and_thyme.services.auth_service

import io.ktor.server.plugins.BadRequestException
import org.jooq.impl.DSL
import rosemary_and_thyme.jooq.app_auth.tables.references.PASSWORD_RESET_TOKENS
import rosemary_and_thyme.jooq.app_auth.tables.references.SESSIONS
import rosemary_and_thyme.jooq.app_auth.tables.references.USERS
import rosemary_and_thyme.models.auth.UserSession
import rosemary_and_thyme.services.AuthService
import rosemary_and_thyme.services.CryptoService
import rosemary_and_thyme.services.Service
import java.time.OffsetDateTime

/**
 * Consumes a reset token and replaces the password and session in one transaction.
 * Other reset tokens and existing sessions are invalidated; the new cookie is set only after commit.
 *
 * @param token raw token delivered by email
 * @param newPassword plaintext replacement password
 * @return the newly created session for the token's owner
 * @throws BadRequestException if the token is invalid, expired, used, or the password is invalid
 */
fun AuthService.confirmPasswordReset(token: String, newPassword: String): UserSession {
    if (!Regex("^[A-Za-z0-9_-]{43}$").matches(token)) {
        throw invalidResetToken()
    }
    if (newPassword.isBlank() || newPassword.length > 1024) {
        throw BadRequestException("Password must not be blank or longer than 1024 characters")
    }
    val tokenHash = CryptoService.sha256(token)
    val passwordHash = CryptoService.hashPassword(newPassword)

    return Service.dsl.transactionResult { configuration ->
        val tx = DSL.using(configuration)
        val userId = tx.select(PASSWORD_RESET_TOKENS.USER_ID)
            .from(PASSWORD_RESET_TOKENS)
            .where(PASSWORD_RESET_TOKENS.TOKEN_HASH.eq(tokenHash))
            .fetchOne()?.value1() ?: throw invalidResetToken()

        // Serialize different reset tokens for the same user before touching token rows.
        tx.select(USERS.ID).from(USERS).where(USERS.ID.eq(userId))
            .forUpdate().fetchOne() ?: throw invalidResetToken()

        val now = OffsetDateTime.now()

        tx.update(PASSWORD_RESET_TOKENS)
            .set(PASSWORD_RESET_TOKENS.USED_AT, now)
            .where(PASSWORD_RESET_TOKENS.TOKEN_HASH.eq(tokenHash))
            .and(PASSWORD_RESET_TOKENS.USED_AT.isNull)
            .and(PASSWORD_RESET_TOKENS.EXPIRES_AT.gt(now))
            .returning(PASSWORD_RESET_TOKENS.USER_ID)
            .fetchOne() ?: throw invalidResetToken()

        check(
            tx.update(USERS)
                .set(USERS.PASSWORD, passwordHash)
                .where(USERS.ID.eq(userId))
                .execute() == 1
        ) { "The password could not be updated" }

        tx.update(PASSWORD_RESET_TOKENS)
            .set(PASSWORD_RESET_TOKENS.USED_AT, now)
            .where(PASSWORD_RESET_TOKENS.USER_ID.eq(userId))
            .and(PASSWORD_RESET_TOKENS.USED_AT.isNull)
            .execute()
        tx.update(SESSIONS)
            .set(SESSIONS.REVOKED, true)
            .where(SESSIONS.USER_ID.eq(userId))
            .and(SESSIONS.REVOKED.eq(false))
            .execute()

        Service.authService.createSession(userId, tx)
    }
}

/** Returns the same error for all rejected reset tokens to avoid revealing token state. */
private fun invalidResetToken() = BadRequestException("The password reset token is invalid or expired")
