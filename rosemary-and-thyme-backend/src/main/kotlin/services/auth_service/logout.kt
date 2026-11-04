package rosemary_and_thyme.services.auth_service

import rosemary_and_thyme.jooq.app_auth.tables.references.SESSIONS
import rosemary_and_thyme.services.AuthService
import rosemary_and_thyme.services.Service
import java.util.UUID

fun AuthService.logout(userId: UUID){
    
    Service.dsl.deleteFrom(SESSIONS)
    .where(SESSIONS.USER_ID.eq(userId))
    .execute() 
}
