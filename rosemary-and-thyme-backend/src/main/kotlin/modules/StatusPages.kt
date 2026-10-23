package rosemary_and_thyme.modules

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.statuspages.StatusPages
import rosemary_and_thyme.models.exceptions.ResourceNotFoundException

fun Application.configureStatusPages(){
    install(StatusPages){
        exception<ResourceNotFoundException> {call,cause->  }

    }
}