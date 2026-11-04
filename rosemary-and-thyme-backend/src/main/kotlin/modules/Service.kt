package rosemary_and_thyme.modules

import io.ktor.server.application.Application
import rosemary_and_thyme.services.Service


/**
 * Initializes [Service] with the application database context, R2 client, and recipe image bucket.
 * Configuration is completed before routing can execute service operations.
 */
fun Application.configureService() {

    Service.initializeDatabase(configureDatabase())
    Service.initializeS3(
        configureS3Client(),
        environment.config.config("s3Client").property("bucket").getString()
    )
}
