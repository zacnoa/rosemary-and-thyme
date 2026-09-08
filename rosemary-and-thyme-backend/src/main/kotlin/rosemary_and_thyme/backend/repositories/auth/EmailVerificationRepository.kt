package rosemary_and_thyme.backend.repositories.auth

import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import rosemary_and_thyme.backend.jooqgenerated.app_auth.tables.records.EmailVerificationTokensRecord
import rosemary_and_thyme.backend.jooqgenerated.app_auth.tables.references.EMAIL_VERIFICATION_TOKENS
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

/** Provides database operations for hashed email-verification tokens. */
@Repository
class EmailVerificationRepository(val dsl: DSLContext) {

    /** Stores a verification token hash for a user.
     * @param userId the account awaiting verification.
     * @param tokenHash the SHA-256 hash of the emailed token.
     */
    fun insertToken(userId: UUID, tokenHash: String) {
        dsl.insertInto(EMAIL_VERIFICATION_TOKENS)
            .set(EMAIL_VERIFICATION_TOKENS.USER_ID, userId)
            .set(EMAIL_VERIFICATION_TOKENS.TOKEN_HASH, tokenHash)
            .execute()
    }

    /** Finds an unused, unexpired verification token.
     * @param tokenHash the SHA-256 hash to look up.
     * @return the matching valid token, or `null` when it is absent, used, or expired.
     */
    fun selectValidToken(tokenHash: String): EmailVerificationTokensRecord? =
        dsl.selectFrom(EMAIL_VERIFICATION_TOKENS)
            .where(EMAIL_VERIFICATION_TOKENS.TOKEN_HASH.eq(tokenHash))
            .and(EMAIL_VERIFICATION_TOKENS.USED_AT.isNull)
            .and(EMAIL_VERIFICATION_TOKENS.EXPIRES_AT.gt(OffsetDateTime.now(ZoneOffset.UTC)))
            .fetchOne()

    /** Marks a verification token as used.
     * @param id the token row to consume.
     */
    fun updateTokenUsedAt(id: UUID) {
        dsl.update(EMAIL_VERIFICATION_TOKENS)
            .set(EMAIL_VERIFICATION_TOKENS.USED_AT, OffsetDateTime.now(ZoneOffset.UTC))
            .where(EMAIL_VERIFICATION_TOKENS.ID.eq(id))
            .execute()
    }

    /** Marks every unused verification token for a user as used.
     * @param userId the account whose outstanding tokens are invalidated.
     */
    fun updateTokensUsedByUserId(userId: UUID) {
        dsl.update(EMAIL_VERIFICATION_TOKENS)
            .set(EMAIL_VERIFICATION_TOKENS.USED_AT, OffsetDateTime.now(ZoneOffset.UTC))
            .where(EMAIL_VERIFICATION_TOKENS.USER_ID.eq(userId))
            .and(EMAIL_VERIFICATION_TOKENS.USED_AT.isNull)
            .execute()
    }
}
