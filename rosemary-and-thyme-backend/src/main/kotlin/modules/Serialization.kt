package rosemary_and_thyme.modules

import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import kotlinx.serialization.json.Json

/**
 * Configures JSON request and response conversion with kotlinx.serialization.
 * Unknown input fields are ignored and explicit `null` properties are omitted from output.
 */
fun Application.configureSerialization(){
    install(ContentNegotiation){
        json(
            Json{
                ignoreUnknownKeys=true
                explicitNulls=false
            }
        )
    }
}
