package rosemary_and_thyme.services.auth_service

import rosemary_and_thyme.jooq.app_auth.tables.references.SESSIONS
import rosemary_and_thyme.jooq.app_auth.tables.references.USERS
import rosemary_and_thyme.models.user.UserPrincipal
import rosemary_and_thyme.models.user.toUserPrincipal
import rosemary_and_thyme.services.AuthService
import rosemary_and_thyme.services.CryptoService
import rosemary_and_thyme.services.Service
import java.time.OffsetDateTime

/**
 * Resolves an active, non-revoked session token to its authenticated user.
 *
 * @param cookie raw session token received from the client
 * @return the authenticated [UserPrincipal], or `null` when the session is invalid or expired
 */
fun AuthService.authenticateSession(cookie:String) : UserPrincipal?=
    
    Service.dsl.select(USERS.ID,USERS.USERNAME,USERS.EMAIL)
    .from(SESSIONS)
    .join(USERS)
    .on(USERS.ID.eq(SESSIONS.USER_ID))
    .where (SESSIONS.SESSION_HASH.eq(CryptoService.sha256(cookie)))
    .and(SESSIONS.EXPIRES_AT.gt(OffsetDateTime.now()))
    .and(SESSIONS.REVOKED.eq(false))
    .fetchOneInto(USERS)
    ?.toUserPrincipal()


