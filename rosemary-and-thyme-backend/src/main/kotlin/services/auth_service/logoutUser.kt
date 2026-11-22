package rosemary_and_thyme.services.auth_service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rosemary_and_thyme.jooq.app_auth.tables.references.SESSIONS
import rosemary_and_thyme.services.AuthService
import rosemary_and_thyme.services.Service
import java.util.UUID


//TODO() Add Only delete current session
suspend fun AuthService.logoutUser(userId: UUID){
    
    withContext(Dispatchers.IO) {
        Service.dsl.deleteFrom(SESSIONS)
        .where(SESSIONS.USER_ID.eq(userId))
        .execute()
    }
}
