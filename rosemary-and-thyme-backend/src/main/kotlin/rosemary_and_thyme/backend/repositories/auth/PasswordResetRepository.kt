package rosemary_and_thyme.backend.repositories.auth

import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import rosemary_and_thyme.backend.jooqgenerated.app_auth.tables.records.PasswordResetTokensRecord
import rosemary_and_thyme.backend.jooqgenerated.app_auth.tables.references.PASSWORD_RESET_TOKENS
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

/**
 * Raw CRUD against `app_auth.password_reset_tokens`. Only ever stores/looks up a
 * token's *hash* - see [rosemary_and_thyme.backend.services.auth.hashToken] for why -
 * so nothing here ever sees a raw reset token. Structurally identical to
 * [EmailVerificationRepository], just against a different table/TTL (1 hour here vs
 * 24 hours for email verification - a reset link is more sensitive to leave valid
 * for long, and is normally used within minutes of being requested).
 */
@Repository
class PasswordResetRepository(val dsl: DSLContext) {

    /**
     * @param userId the token's owner
     * @param tokenHash the SHA-256 hash of the raw reset token
     */
    fun insertToken(userId: UUID, tokenHash: String) {
        dsl.insertInto(PASSWORD_RESET_TOKENS)
            .set(PASSWORD_RESET_TOKENS.USER_ID, userId)
            .set(PASSWORD_RESET_TOKENS.TOKEN_HASH, tokenHash)
            .execute()
    }

    /**
     * Only returns the token if it hasn't been used yet and hasn't expired.
     *
     * @param tokenHash the SHA-256 hash of the raw token from the emailed link
     * @return the matching, still-valid token row, or `null` if no row matches the
     * hash, or it matches but is already used/expired
     */
    fun selectValidToken(tokenHash: String): PasswordResetTokensRecord? =
        dsl.selectFrom(PASSWORD_RESET_TOKENS)
            .where(PASSWORD_RESET_TOKENS.TOKEN_HASH.eq(tokenHash))
            .and(PASSWORD_RESET_TOKENS.USED_AT.isNull)
            .and(PASSWORD_RESET_TOKENS.EXPIRES_AT.gt(OffsetDateTime.now(ZoneOffset.UTC)))
            .fetchOne()

    /** Marks a token as consumed, by setting `used_at` to now. */
    fun updateTokenUsedAt(id: UUID) {
        dsl.update(PASSWORD_RESET_TOKENS)
            .set(PASSWORD_RESET_TOKENS.USED_AT, OffsetDateTime.now(ZoneOffset.UTC))
            .where(PASSWORD_RESET_TOKENS.ID.eq(id))
            .execute()
    }

    /**
     * Invalidates any outstanding tokens for a user before issuing a new one (called
     * on every fresh request, so a stale link can't be used after a newer one was
     * requested).
     *
     * @param userId invalidate every not-yet-used token belonging to this user
     */
    fun updateTokensUsedByUserId(userId: UUID) {
        dsl.update(PASSWORD_RESET_TOKENS)
            .set(PASSWORD_RESET_TOKENS.USED_AT, OffsetDateTime.now(ZoneOffset.UTC))
            .where(PASSWORD_RESET_TOKENS.USER_ID.eq(userId))
            .and(PASSWORD_RESET_TOKENS.USED_AT.isNull)
            .execute()
    }
}
