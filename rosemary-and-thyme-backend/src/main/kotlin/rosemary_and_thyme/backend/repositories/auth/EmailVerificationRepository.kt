package rosemary_and_thyme.backend.repositories.auth

import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import rosemary_and_thyme.backend.jooqgenerated.app_auth.tables.records.EmailVerificationTokensRecord
import rosemary_and_thyme.backend.jooqgenerated.app_auth.tables.references.EMAIL_VERIFICATION_TOKENS
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

/**
 * Raw CRUD against `app_auth.email_verification_tokens`. Only ever stores/looks up a
 * token's *hash* - see [rosemary_and_thyme.backend.services.auth.hashToken] for why -
 * so nothing here ever sees a raw verification token.
 */
@Repository
class EmailVerificationRepository(val dsl: DSLContext) {

    /**
     * @param userId the token's owner
     * @param tokenHash the SHA-256 hash of the raw verification token
     */
    fun insertToken(userId: UUID, tokenHash: String) {
        dsl.insertInto(EMAIL_VERIFICATION_TOKENS)
            .set(EMAIL_VERIFICATION_TOKENS.USER_ID, userId)
            .set(EMAIL_VERIFICATION_TOKENS.TOKEN_HASH, tokenHash)
            .execute()
    }

    /**
     * Only returns the token if it hasn't been used yet and hasn't expired.
     *
     * @param tokenHash the SHA-256 hash of the raw token from the emailed link
     * @return the matching, still-valid token row, or `null` if no row matches the
     * hash, or it matches but is already used/expired
     */
    fun selectValidToken(tokenHash: String): EmailVerificationTokensRecord? =
        dsl.selectFrom(EMAIL_VERIFICATION_TOKENS)
            .where(EMAIL_VERIFICATION_TOKENS.TOKEN_HASH.eq(tokenHash))
            .and(EMAIL_VERIFICATION_TOKENS.USED_AT.isNull)
            .and(EMAIL_VERIFICATION_TOKENS.EXPIRES_AT.gt(OffsetDateTime.now(ZoneOffset.UTC)))
            .fetchOne()

    /** Marks a token as consumed, by setting `used_at` to now. */
    fun updateTokenUsedAt(id: UUID) {
        dsl.update(EMAIL_VERIFICATION_TOKENS)
            .set(EMAIL_VERIFICATION_TOKENS.USED_AT, OffsetDateTime.now(ZoneOffset.UTC))
            .where(EMAIL_VERIFICATION_TOKENS.ID.eq(id))
            .execute()
    }

    /**
     * Invalidates any outstanding tokens for a user before issuing a new one
     * (called on resend, so a stale link can't be used after a fresh one was requested).
     *
     * @param userId invalidate every not-yet-used token belonging to this user
     */
    fun updateTokensUsedByUserId(userId: UUID) {
        dsl.update(EMAIL_VERIFICATION_TOKENS)
            .set(EMAIL_VERIFICATION_TOKENS.USED_AT, OffsetDateTime.now(ZoneOffset.UTC))
            .where(EMAIL_VERIFICATION_TOKENS.USER_ID.eq(userId))
            .and(EMAIL_VERIFICATION_TOKENS.USED_AT.isNull)
            .execute()
    }
}
