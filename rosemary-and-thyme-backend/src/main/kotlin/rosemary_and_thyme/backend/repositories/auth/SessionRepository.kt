package rosemary_and_thyme.backend.repositories.auth

import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import rosemary_and_thyme.backend.jooqgenerated.auth.tables.records.SessionsRecord
import rosemary_and_thyme.backend.jooqgenerated.auth.tables.references.SESSIONS
import java.time.Duration
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

@Repository
class SessionRepository(val dsl: DSLContext) {


    fun insertSession(userId: UUID,hashedToken:String): String =
        dsl.insertInto(SESSIONS)
            .set(SESSIONS.USER_ID, userId)
            .set(SESSIONS.SESSION_HASH, hashedToken)
            .returning()
            .fetchOne()?.sessionHash ?: throw IllegalStateException("Failed to insert session")

    fun fetchSession(sessionHash: String ): SessionsRecord? =
        dsl.selectFrom(SESSIONS)
            .where(SESSIONS.SESSION_HASH.eq(sessionHash))
            .fetchOne()

    fun prolongSessionExpiration(sessionId: UUID, extension: Duration) {
        dsl.update(SESSIONS)
            .set(SESSIONS.EXPIRES_AT, OffsetDateTime.now(ZoneOffset.UTC).plus(extension))
            .where(SESSIONS.ID.eq(sessionId))
            .execute()
    }

    fun revokeSession(sessionId:UUID){

        dsl.update(SESSIONS)
            .set(SESSIONS.REVOKED,true)
            .where(SESSIONS.ID.eq(sessionId))
            .execute()
    }

}
