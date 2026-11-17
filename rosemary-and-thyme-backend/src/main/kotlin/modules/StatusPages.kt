package rosemary_and_thyme.modules

import com.resend.core.exception.ResendException

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.ContentTransformationException
import io.ktor.server.request.uri
import io.ktor.server.response.header
import io.ktor.server.response.respond
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import rosemary_and_thyme.models.exceptions.AuthenticationException
import rosemary_and_thyme.models.exceptions.EmailNotVerified
import rosemary_and_thyme.models.exceptions.BadRecipeFormat
import rosemary_and_thyme.models.exceptions.RecipeImageUploadException
import rosemary_and_thyme.models.exceptions.RecipeVersionConflict
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
        exception<EmailNotVerified> { call, cause ->
            call.respondProblem(
                status = HttpStatusCode.Forbidden,
                type = "problem/email-not-verified",
                title = "Email not verified",
                detail = cause.message ?: "Please verify your email address before logging in"
            )
        }
        exception<ResendException> { call, _ ->
            call.respondProblem(
                status = HttpStatusCode.BadGateway,
                type = "problem/email-delivery",
                title = "Email delivery failed",
                detail = "The email could not be sent. Please request a new link."
            )
        }
        exception<AuthenticationException> { call, cause ->
            call.respondProblem(
                status = HttpStatusCode.Unauthorized,
                type = "problem/authentication",
                title = "Authentication failed",
                detail = cause.message ?: "Authentication failed"
            )
        }
        exception<BadRequestException> { call, cause ->
            call.respondProblem(
                status = HttpStatusCode.BadRequest,
                type = "problem/bad-request",
                title = "Bad Request",
                detail = cause.message ?: "Bad Request"
            )
        }
        exception<BadRecipeFormat> { call, cause ->
            call.respondProblem(
                status = HttpStatusCode.BadRequest,
                type = "problem/invalid-recipe",
                title = "Invalid recipe",
                detail = cause.message ?: "The recipe does not match the expected format"
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
        exception<RecipeVersionConflict> { call, cause ->
            call.respondProblem(
                status = HttpStatusCode.Conflict,
                type = "problem/recipe-version-conflict",
                title = "Recipe was modified",
                detail = cause.message ?: "Reload the recipe before saving again"
            )
        }
        exception<RecipeImageUploadException> { call, cause ->
            call.respondProblem(
                status = HttpStatusCode.Conflict,
                type = "problem/recipe-images-incomplete",
                title = "Recipe images are incomplete",
                detail = cause.message ?: "The recipe images have not been uploaded correctly"
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
    exception<ContentTransformationException> { call, _ ->
        call.respondProblem(
            status = HttpStatusCode.BadRequest,
            type = "problem/invalid-request-body",
            title = "Invalid request body",
            detail = "The request body does not match the expected format"
        )
    }

    }
}
