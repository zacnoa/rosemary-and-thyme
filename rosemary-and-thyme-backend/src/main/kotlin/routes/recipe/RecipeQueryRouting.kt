package rosemary_and_thyme.routes.recipe

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions
import rosemary_and_thyme.models.auth.UserSession
import rosemary_and_thyme.routes.utils.requireUuidPathParameter
import rosemary_and_thyme.services.Service
import rosemary_and_thyme.services.auth_service.authenticateSession
import rosemary_and_thyme.services.recipe_service.getRecipeById
import rosemary_and_thyme.services.recipe_service.searchRecipes
import rosemary_and_thyme.services.s3_service.mediaAccessToken

/**
 * Registers recipe search and detail routes under `/recipe`.
 * Search resolves the session when present, returns a page as a JSON array, and sends the next
 * cursor in `X-Next-Cursor`. Each search image URL carries a JWT scoped to that image.
 * The detail route checks recipe visibility before issuing image-scoped Worker URLs.
 */
fun Route.recipeQueryingRoutes() {
    get("/search") {
        val params = call.request.queryParameters
        val query = params["q"]?.trim().orEmpty()
        if (query.length !in 1..100) {
            throw BadRequestException("q must contain 1 to 100 characters")
        }

        val limit = params["limit"]?.let {
            it.toIntOrNull()?.takeIf { value -> value in 1..50 }
                ?: throw BadRequestException("limit must be between 1 and 50")
        } ?: 20

        val userPrincipal = call.sessions.get<UserSession>()?.let { session ->
            Service.authService.authenticateSession(session.cookie)
        }

        val page = Service.recipeService.searchRecipes(
            query = query,
            limit = limit,
            cursorToken = params["cursor"],
            userPrincipal = userPrincipal
        )

        val workerBaseUrl = call.application.environment.config
            .property("recipeMedia.workerBaseUrl").getString().trimEnd('/')
        val items = page.items.mapTo(linkedSetOf()) { item ->
            val imageId = item.imageId ?: return@mapTo item

            val mediaAccessToken = Service.s3Service.mediaAccessToken(
                workerBaseUrl = workerBaseUrl,
                userId = item.userId,
                recipeId = item.recipeId,
                imageId = imageId
            )

            item.copy(
                imageUrl = mediaAccessToken
            )
        }

        call.response.header(HttpHeaders.CacheControl, "private, no-store")
        page.nextCursor?.let { call.response.header("X-Next-Cursor", it) }
        call.respond(status = HttpStatusCode.OK, items)
    }
    // Returns a recipe identified by a UUID path parameter.
    get("/{id}") {
        val recipeId = call.requireUuidPathParameter("id")

        val workerBaseUrl = call.application.environment.config
            .property("recipeMedia.workerBaseUrl").getString().trimEnd('/')
        val userPrincipal = call.sessions.get<UserSession>()?.let { session ->
            Service.authService.authenticateSession(session.cookie)
        }

        val recipe = Service.recipeService.getRecipeById(recipeId, userPrincipal)
        // Replace placeholder image IDs with image-scoped Worker URLs.
        val recipeWithUrls = recipe.copy(
            images = recipe.images.keys.associateWith { id ->
                Service.s3Service.mediaAccessToken(
                    workerBaseUrl = workerBaseUrl,
                    recipeId = recipeId.toString(),
                    userId = recipe.userId,
                    imageId = id
                )
            }
        )

        call.response.header(HttpHeaders.CacheControl, "private, no-store")
        call.respond(status = HttpStatusCode.OK, message = recipeWithUrls)
    }
}
