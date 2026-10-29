package rosemary_and_thyme.modules

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.uri
import io.ktor.server.response.header
import io.ktor.server.response.respond
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import rosemary_and_thyme.models.exceptions.ResourceNotFoundException
import rosemary_and_thyme.models.exceptions.UserAlreadyExists


/**
 * Responds with an RFC 9457 problem-details document using `application/problem+json`.
 *
 * @param status HTTP status returned to the client
 * @param type identifier for the problem category
 * @param title short, human-readable problem summary
 * @param detail explanation specific to this occurrence
 */
suspend fun ApplicationCall.respondProblem(status: HttpStatusCode, type: String, title: String, detail: String) {

    response.header(HttpHeaders.ContentType, ContentType.Application.ProblemJson.toString())

    //RFC 9457 Problem Details
    respond(
        status = status,
        message = buildJsonObject {
            put("type", type)
            put("title", title)
            put("status", status.value)
            put("detail", detail)
            put("instance", request.uri)
        }

    )
}

/**
 * Maps known application exceptions to RFC 9457 responses.
 * Unexpected illegal states are returned as generic internal-server errors.
 */
fun Application.configureStatusPages() {
    install(StatusPages) {
        exception<BadRequestException> { call, cause ->
            call.respondProblem(
                status = HttpStatusCode.BadRequest,
                type = "problem/bad-request",
                title = "Bad Request",
                detail = cause.message ?: "Bad Request"
            )
        }
        exception<ResourceNotFoundException> { call, cause ->
            call.respondProblem(
                status = HttpStatusCode.NotFound,
                type = "problem/not-found",
                title = "Resource not found",
                detail = cause.message ?: "Resource not found"
            )

        }
        exception<UserAlreadyExists> { call, cause ->
            call.respondProblem(
                status = HttpStatusCode.Conflict,
                type = "problem/user-exists",
                title = "Username already exists",
                detail = cause.message ?: "A user with that username already exists"
            )
        }
        exception<IllegalStateException> { call, cause ->
            call.respondProblem(
                status = HttpStatusCode.InternalServerError,
                type = "problem/internal",
                title = "Something went wrong",
                detail = "Something went wrong on our end, Please try again later or contact us at"
            )
        }

    }
}
