package rosemary_and_thyme.backend.repositories.auth

import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import rosemary_and_thyme.backend.jooqgenerated.app_auth.tables.records.SessionsRecord
import rosemary_and_thyme.backend.jooqgenerated.app_auth.tables.references.SESSIONS
import java.time.Duration
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

/**
 * Raw CRUD against `app_auth.sessions`. Only ever stores/looks up a session's *hash* -
 * see [rosemary_and_thyme.backend.services.auth.hashToken] for why - so nothing here
 * ever sees a raw session token.
 */
@Repository
class SessionRepository(val dsl: DSLContext) {


    /**
     * @param userId the session's owner
     * @param hashedToken the SHA-256 hash of the raw session token (never the raw token itself)
     * @return the stored `session_hash` (i.e. [hashedToken] echoed back), confirming the insert succeeded
     */
    fun insertSession(userId: UUID,hashedToken:String): String =
        dsl.insertInto(SESSIONS)
            .set(SESSIONS.USER_ID, userId)
            .set(SESSIONS.SESSION_HASH, hashedToken)
            .returning()
            .fetchOne()?.sessionHash ?: throw IllegalStateException("Failed to insert session")

    /** @return the session row whose hash matches [sessionHash], or `null` if none exists */
    fun selectSessionByHash(sessionHash: String ): SessionsRecord? =
        dsl.selectFrom(SESSIONS)
            .where(SESSIONS.SESSION_HASH.eq(sessionHash))
            .fetchOne()

    /**
     * Pushes a session's expiry further into the future by [extension]. Not currently
     * called anywhere (sessions today expire at their original 30-day TTL rather than
     * sliding on activity).
     */
    fun updateSessionExpiration(sessionId: UUID, extension: Duration) {
        dsl.update(SESSIONS)
            .set(SESSIONS.EXPIRES_AT, OffsetDateTime.now(ZoneOffset.UTC).plus(extension))
            .where(SESSIONS.ID.eq(sessionId))
            .execute()
    }

    /** Marks a session revoked (logout). Revoked sessions fail [rosemary_and_thyme.backend.services.auth.SessionService.validateSession] even before their TTL expires. */
    fun updateSessionRevoked(sessionId:UUID){

        dsl.update(SESSIONS)
            .set(SESSIONS.REVOKED,true)
            .where(SESSIONS.ID.eq(sessionId))
            .execute()
    }

    /**
     * Revokes every not-already-revoked session belonging to a user in one statement -
     * used after a password change/reset (see
     * [rosemary_and_thyme.backend.services.auth.SessionService.revokeAllSessions]) so a
     * stolen or shared session elsewhere can't outlive the credential that granted it.
     */
    fun updateAllSessionsRevokedByUserId(userId: UUID) {
        dsl.update(SESSIONS)
            .set(SESSIONS.REVOKED, true)
            .where(SESSIONS.USER_ID.eq(userId))
            .and(SESSIONS.REVOKED.eq(false))
            .execute()
    }

    /**
     * Hard-deletes every session row that can no longer authenticate anything - either
     * revoked (logout, or a password change/reset via [updateAllSessionsRevokedByUserId])
     * or past its `expires_at` - backing
     * [rosemary_and_thyme.backend.services.auth.SessionService.purgeInvalidSessions]'s
     * daily cleanup job. These rows are already permanently rejected by
     * [rosemary_and_thyme.backend.services.auth.SessionService.validateSession], so
     * deleting them changes no observable behavior; this purely keeps the table from
     * growing unbounded with rows nothing will ever look up again (see
     * `sessions_revoked_expires_at_idx` in db/indexes.sql for the index this query
     * uses).
     *
     * @return how many rows were deleted, purely for logging
     */
    fun deleteInvalidSessions(): Int =
        dsl.deleteFrom(SESSIONS)
            .where(SESSIONS.REVOKED.eq(true))
            .or(SESSIONS.EXPIRES_AT.lt(OffsetDateTime.now(ZoneOffset.UTC)))
            .execute()

}
