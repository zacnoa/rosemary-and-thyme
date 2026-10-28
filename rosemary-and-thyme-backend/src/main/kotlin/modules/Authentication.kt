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

fun Application.configureAuthentication(){
    install(Authentication){
        session<UserSession>("auth-session"){
            validate { session->
                Service.authService.authenticateSession(cookie = session.cookie)
             }
            challenge {
                call.respondProblem(status= HttpStatusCode.Unauthorized,type="problem/authentication",title="Unauthenticated",detail="The session is invalid")
            }
        }

    }
}