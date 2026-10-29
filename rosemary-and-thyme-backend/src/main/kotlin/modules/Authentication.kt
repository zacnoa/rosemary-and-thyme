package rosemary_and_thyme.modules

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.basic
import io.ktor.server.auth.install
import io.ktor.server.auth.session
import rosemary_and_thyme.models.auth.UserSession
import rosemary_and_thyme.services.Service
import rosemary_and_thyme.services.auth_service.authenticateSession

/**
 * Configures session-based authentication.
 * Invalid, expired, or revoked sessions are challenged with an RFC 9457 response.
 *
 * Routes protected by `authenticate("auth-session")` run only after validation returns a
 * non-null [rosemary_and_thyme.models.user.UserPrincipal]. Within those route handlers,
 * `checkNotNull` is needed only because Ktor's principal lookup API has a nullable return type.
 */
fun Application.configureAuthentication(){
    install(Authentication){
        session<UserSession>("auth-session"){
            // A non-null principal allows the request to continue to protected routes.
            validate { session->
                Service.authService.authenticateSession(cookie = session.cookie)
             }
            // A null principal means the session could not authenticate the request.
            challenge {
                call.respondProblem(status= HttpStatusCode.Unauthorized,type="problem/authentication",title="Unauthenticated",detail="The session is invalid")
            }
        }

    }
}
