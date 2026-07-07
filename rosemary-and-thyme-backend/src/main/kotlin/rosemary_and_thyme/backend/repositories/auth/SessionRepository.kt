/*
package rosemary_and_thyme.backend.repositories.auth

import org.jooq.DSLContext
import rosemary_and_thyme.backend.jooqgenerated.auth.tables.records.SessionsRecord
import rosemary_and_thyme.backend.jooqgenerated.auth.tables.references.SESSIONS
import java.util.UUID

class SessionRepository(val dsl: DSLContext) {


    fun insertSession(userId: UUID):UUID =
        dsl.insertInto(SESSIONS)
            .set(SESSIONS.USER_ID, userId)
            .returning()
            .fetchOne()!!.id!!

    fun fetchSession(sessionId:UUID): SessionsRecord? =
        dsl.selectFrom(SESSIONS)
            .where(SESSIONS.ID.eq(sessionId))
            .fetchOne()
}*/
