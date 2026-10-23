package rosemary_and_thyme.modules

import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import rosemary_and_thyme.services.Service


fun Application.configureService(){

    Service.initialize(configureDatabase())
    
    
    monitor.subscribe(ApplicationStopped){
        Service.clear()
    }

}