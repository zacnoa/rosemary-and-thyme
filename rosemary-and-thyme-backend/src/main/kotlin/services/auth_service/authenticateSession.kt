package rosemary_and_thyme.services.auth_service

import rosemary_and_thyme.extensions.sha256
import rosemary_and_thyme.jooq.app_auth.tables.references.SESSIONS
import rosemary_and_thyme.jooq.app_auth.tables.references.USERS
import rosemary_and_thyme.models.exceptions.AuthenticationException
import rosemary_and_thyme.models.user.UserPrincipal
import rosemary_and_thyme.services.AuthService
import rosemary_and_thyme.services.Service.dsl
import java.time.OffsetDateTime

fun AuthService.authenticateSession(cookie:String) : UserPrincipal?=
    
    dsl.select(USERS.ID,USERS.USERNAME,USERS.EMAIL)
    .from(SESSIONS)
    .join(USERS)
    .on(USERS.ID.eq(SESSIONS.USER_ID))
    .where (SESSIONS.SESSION_HASH.eq(cookie.sha256()))
    .and(SESSIONS.EXPIRES_AT.gt(OffsetDateTime.now()))
    .and(SESSIONS.REVOKED.eq(false))
    .fetchOne()?.let{record->
            UserPrincipal(
                id=checkNotNull(record.getValue(USERS.ID)),
                username = checkNotNull(record.getValue(USERS.USERNAME)),
                email=checkNotNull(record.getValue(USERS.EMAIL))
            )
        }     


