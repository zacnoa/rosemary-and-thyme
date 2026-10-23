package rosemary_and_thyme.modules

import io.ktor.server.application.Application


fun Application.modules(){

    configureSession()
    configureAuthentication()
    configureRouting()
    configureSerialization()
    configureStatusPages()

    
    configureService()

}