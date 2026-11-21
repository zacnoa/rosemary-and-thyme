package rosemary_and_thyme.modules

import io.ktor.server.application.Application
import rosemary_and_thyme.services.CryptoService
import rosemary_and_thyme.services.Service


/**
 * Initializes the database, R2, and Resend clients used by [Service], together with JWT signing keys.
 * Configuration is completed before routing can execute service operations or issue media tokens.
 */
fun Application.configureService() {

    Service.initializeDatabase(configureDatabase())
    Service.initializeS3(
        configureS3Client(),
        environment.config.config("s3Client").property("bucket").getString()
    )
    Service.initializeEmail(
        client = configureResendClient(),
        sender = environment.config.config("resend").property("from").getString(),
        frontendUrl = environment.config.config("frontend").property("url").getString()
    )
    CryptoService.initializeJwtAlgorithm(
        publicKey = environment.config.config("jwt").property("publicKey").getString(),
        privateKey = environment.config.config("jwt").property("privateKey").getString()
    )
}
