package rosemary_and_thyme.backend.repositories.auth

import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import rosemary_and_thyme.backend.jooqgenerated.app_auth.tables.records.PasswordResetTokensRecord
import rosemary_and_thyme.backend.jooqgenerated.app_auth.tables.references.PASSWORD_RESET_TOKENS
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

/** Provides database operations for hashed password-reset tokens. */
@Repository
class PasswordResetRepository(val dsl: DSLContext) {

    /** Stores a password-reset token hash for a user.
     * @param userId the account requesting a reset.
     * @param tokenHash the SHA-256 hash of the emailed token.
     */
    fun insertToken(userId: UUID, tokenHash: String) {
        dsl.insertInto(PASSWORD_RESET_TOKENS)
            .set(PASSWORD_RESET_TOKENS.USER_ID, userId)
            .set(PASSWORD_RESET_TOKENS.TOKEN_HASH, tokenHash)
            .execute()
    }

    /** Finds an unused, unexpired reset token.
     * @param tokenHash the SHA-256 hash to look up.
     * @return the matching valid token, or `null` when it is absent, used, or expired.
     */
    fun selectValidToken(tokenHash: String): PasswordResetTokensRecord? =
        dsl.selectFrom(PASSWORD_RESET_TOKENS)
            .where(PASSWORD_RESET_TOKENS.TOKEN_HASH.eq(tokenHash))
            .and(PASSWORD_RESET_TOKENS.USED_AT.isNull)
            .and(PASSWORD_RESET_TOKENS.EXPIRES_AT.gt(OffsetDateTime.now(ZoneOffset.UTC)))
            .fetchOne()

    /** Marks a reset token as used.
     * @param id the token row to consume.
     */
    fun updateTokenUsedAt(id: UUID) {
        dsl.update(PASSWORD_RESET_TOKENS)
            .set(PASSWORD_RESET_TOKENS.USED_AT, OffsetDateTime.now(ZoneOffset.UTC))
            .where(PASSWORD_RESET_TOKENS.ID.eq(id))
            .execute()
    }

    /** Marks every unused reset token for a user as used.
     * @param userId the account whose outstanding tokens are invalidated.
     */
    fun updateTokensUsedByUserId(userId: UUID) {
        dsl.update(PASSWORD_RESET_TOKENS)
            .set(PASSWORD_RESET_TOKENS.USED_AT, OffsetDateTime.now(ZoneOffset.UTC))
            .where(PASSWORD_RESET_TOKENS.USER_ID.eq(userId))
            .and(PASSWORD_RESET_TOKENS.USED_AT.isNull)
            .execute()
    }
}
