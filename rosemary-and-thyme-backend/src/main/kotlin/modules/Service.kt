package rosemary_and_thyme.modules

import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import rosemary_and_thyme.services.Service


/**
 * Initializes [Service] with the application database context.
 * The stored context is cleared when the application stops.
 */
fun Application.configureService(){

    Service.initialize(configureDatabase())
    
    
    monitor.subscribe(ApplicationStopped){
        Service.clear()
    }

}
