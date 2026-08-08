package rosemary_and_thyme.backend.repositories.auth

import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import rosemary_and_thyme.backend.jooqgenerated.app_auth.tables.records.EmailVerificationTokensRecord
import rosemary_and_thyme.backend.jooqgenerated.app_auth.tables.references.EMAIL_VERIFICATION_TOKENS
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

@Repository
class EmailVerificationRepository(val dsl: DSLContext) {

    fun insertToken(userId: UUID, tokenHash: String) {
        dsl.insertInto(EMAIL_VERIFICATION_TOKENS)
            .set(EMAIL_VERIFICATION_TOKENS.USER_ID, userId)
            .set(EMAIL_VERIFICATION_TOKENS.TOKEN_HASH, tokenHash)
            .execute()
    }

    /**
     * Only returns the token if it hasn't been used yet and hasn't expired.
     */
    fun fetchValidToken(tokenHash: String): EmailVerificationTokensRecord? =
        dsl.selectFrom(EMAIL_VERIFICATION_TOKENS)
            .where(EMAIL_VERIFICATION_TOKENS.TOKEN_HASH.eq(tokenHash))
            .and(EMAIL_VERIFICATION_TOKENS.USED_AT.isNull)
            .and(EMAIL_VERIFICATION_TOKENS.EXPIRES_AT.gt(OffsetDateTime.now(ZoneOffset.UTC)))
            .fetchOne()

    fun markUsed(id: UUID) {
        dsl.update(EMAIL_VERIFICATION_TOKENS)
            .set(EMAIL_VERIFICATION_TOKENS.USED_AT, OffsetDateTime.now(ZoneOffset.UTC))
            .where(EMAIL_VERIFICATION_TOKENS.ID.eq(id))
            .execute()
    }

    /**
     * Invalidates any outstanding tokens for a user before issuing a new one
     * (called on resend, so a stale link can't be used after a fresh one was requested).
     */
    fun invalidateAllForUser(userId: UUID) {
        dsl.update(EMAIL_VERIFICATION_TOKENS)
            .set(EMAIL_VERIFICATION_TOKENS.USED_AT, OffsetDateTime.now(ZoneOffset.UTC))
            .where(EMAIL_VERIFICATION_TOKENS.USER_ID.eq(userId))
            .and(EMAIL_VERIFICATION_TOKENS.USED_AT.isNull)
            .execute()
    }
}
