package rosemary_and_thyme.modules

import io.ktor.server.application.Application


/** Installs the application plugins and initializes the service layer. */
fun Application.modules(){

    configureSession()
    configureAuthentication()
    configureRouting()
    configureSerialization()
    configureStatusPages()

    
    configureService()

}
