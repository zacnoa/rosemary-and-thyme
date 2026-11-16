package rosemary_and_thyme.modules

import io.ktor.server.application.Application


/**
 * Installs application plugins and initializes shared services.
 * The function is suspending because OIDC provider discovery runs during startup.
 */
suspend fun Application.modules(){

    configureSession()
    configureAuthentication()
    configureOIDC()
    configureSerialization()
    configureStatusPages()
    configureRouting()

    
    configureService()
    configureCron()

}
