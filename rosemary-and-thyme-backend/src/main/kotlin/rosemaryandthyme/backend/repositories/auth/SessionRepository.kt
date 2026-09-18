package rosemaryandthyme.backend.repositories.auth

import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import rosemaryandthyme.backend.jooqgenerated.app_auth.tables.records.SessionsRecord
import rosemaryandthyme.backend.jooqgenerated.app_auth.tables.references.SESSIONS
import java.time.Duration
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

/** Provides database operations for hashed application sessions. */
@Repository
class SessionRepository(val dsl: DSLContext) {


    /** Stores a new session for a user.
     * @param userId the session owner.
     * @param hashedToken the SHA-256 hash of the session token.
     * @return the stored token hash.
     * @throws IllegalStateException if the insert does not return a row.
     */
    fun insertSession(userId: UUID,hashedToken:String): String =
        dsl.insertInto(SESSIONS)
            .set(SESSIONS.USER_ID, userId)
            .set(SESSIONS.SESSION_HASH, hashedToken)
            .returning()
            .fetchOne()?.sessionHash ?: throw IllegalStateException("Failed to insert session")

    /** Finds a session by its token hash.
     * @param sessionHash the SHA-256 hash to look up.
     * @return the matching session, or `null` when none exists.
     */
    fun selectSessionByHash(sessionHash: String ): SessionsRecord? =
        dsl.selectFrom(SESSIONS)
            .where(SESSIONS.SESSION_HASH.eq(sessionHash))
            .fetchOne()

    /** Extends a session's expiry time.
     * @param sessionId the session to extend.
     * @param extension the duration added to the current time.
     */
    fun updateSessionExpiration(sessionId: UUID, extension: Duration) {
        dsl.update(SESSIONS)
            .set(SESSIONS.EXPIRES_AT, OffsetDateTime.now(ZoneOffset.UTC).plus(extension))
            .where(SESSIONS.ID.eq(sessionId))
            .execute()
    }

    /** Revokes one session.
     * @param sessionId the session to revoke.
     */
    fun updateSessionRevoked(sessionId:UUID){

        dsl.update(SESSIONS)
            .set(SESSIONS.REVOKED,true)
            .where(SESSIONS.ID.eq(sessionId))
            .execute()
    }

    /** Revokes every active session owned by a user.
     * @param userId the user whose sessions are revoked.
     */
    fun updateAllSessionsRevokedByUserId(userId: UUID) {
        dsl.update(SESSIONS)
            .set(SESSIONS.REVOKED, true)
            .where(SESSIONS.USER_ID.eq(userId))
            .and(SESSIONS.REVOKED.eq(false))
            .execute()
    }

    /** Deletes revoked and expired sessions.
     * @return the number of deleted session rows.
     */
    fun deleteInvalidSessions(): Int =
        dsl.deleteFrom(SESSIONS)
            .where(SESSIONS.REVOKED.eq(true))
            .or(SESSIONS.EXPIRES_AT.lt(OffsetDateTime.now(ZoneOffset.UTC)))
            .execute()

}
