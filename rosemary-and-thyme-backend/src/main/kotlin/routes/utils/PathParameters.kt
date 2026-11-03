package rosemary_and_thyme.routes.utils

import io.ktor.server.application.ApplicationCall
import io.ktor.server.plugins.BadRequestException
import java.util.UUID

/**
 * Reads a required path parameter as a UUID.
 *
 * @param name name of the path parameter
 * @return the parsed UUID
 * @throws BadRequestException if the parameter is missing or is not a valid UUID
 */
fun ApplicationCall.requireUuidPathParameter(name: String): UUID {
    val value = pathParameters[name]
        ?: throw BadRequestException("The '$name' path parameter is required")

    return runCatching { UUID.fromString(value) }
        .getOrElse {
            throw BadRequestException(
                "The '$name' path parameter must be a valid UUID"
            )
        }
}
