package rosemary_and_thyme.modules

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.basic
import io.ktor.server.auth.install
import io.ktor.server.auth.session
import rosemary_and_thyme.models.auth.UserSession

fun Application.configureAuthentication(){
    install(Authentication){
        session<UserSession>("auth-session"){
            validate { session-> }
            challenge {  }
        }

    }
}