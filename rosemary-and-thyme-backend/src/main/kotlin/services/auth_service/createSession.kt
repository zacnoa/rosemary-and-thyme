package rosemary_and_thyme.services.auth_service

import rosemary_and_thyme.jooq.app_auth.tables.references.SESSIONS
import rosemary_and_thyme.models.auth.UserSession
import rosemary_and_thyme.services.AuthService
import rosemary_and_thyme.services.CryptoService
import rosemary_and_thyme.services.Service.dsl
import java.util.UUID

fun AuthService.createSession(userId: UUID): UserSession{
    val token= CryptoService.generateRandomToken()
    val hashedToken= CryptoService.sha256(token)
    
    dsl.insertInto(SESSIONS)
    .set(SESSIONS.SESSION_HASH,hashedToken)
    .set(SESSIONS.USER_ID,userId)
    .execute().also{affectedRow->
        check(affectedRow==0){"The user session could not be inserted into the database"}
    }
    return UserSession(token)
}