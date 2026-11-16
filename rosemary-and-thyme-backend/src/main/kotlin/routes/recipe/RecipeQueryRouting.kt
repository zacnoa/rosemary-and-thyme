package rosemary_and_thyme.routes.recipe

import com.auth0.jwt.JWT
import io.ktor.http.Cookie
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rosemary_and_thyme.models.auth.UserSession
import rosemary_and_thyme.routes.utils.requireUuidPathParameter
import rosemary_and_thyme.services.CryptoService
import rosemary_and_thyme.services.Service
import rosemary_and_thyme.services.auth_service.authenticateSession
import rosemary_and_thyme.services.recipe_service.getRecipeById
import rosemary_and_thyme.services.recipe_service.searchRecipes
import java.time.Instant

/**
 * Registers recipe search and detail routes under `/recipe`.
 * Search resolves the session when present, returns a page as a JSON array, and sends the next
 * cursor in `X-Next-Cursor`. Each search image URL carries a JWT scoped to that image.
 * The detail route issues an HTTP-only media cookie scoped to the recipe.
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
            withContext(Dispatchers.IO) {
                Service.authService.authenticateSession(session.cookie)
            }
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
            val jwt = JWT.create()
                .withIssuer("rosemary-and-thyme")
                .withAudience("rosemary-and-thyme-worker")
                .withClaim("userId", item.userId)
                .withClaim("recipeId", item.recipeId)
                .withClaim("imageId", imageId)
                .withExpiresAt(Instant.now().plusSeconds(15 * 60))
                .sign(CryptoService.jwtAlgorithm)

            item.copy(
                imageUrl = "$workerBaseUrl/recipe-images/${item.userId}/${item.recipeId}/$imageId?token=$jwt"
            )
        }

        call.response.header(HttpHeaders.CacheControl, "private, no-store")
        page.nextCursor?.let { call.response.header("X-Next-Cursor", it) }
        call.respond(items)
    }
    // Returns a recipe identified by a UUID path parameter.
    get("/{id}") {


        val id = call.requireUuidPathParameter("id")
        
        val recipe=Service.recipeService.getRecipeById(id)
        val jwt= JWT.create()
        .withIssuer("rosemary-and-thyme")
        .withAudience("rosemary-and-thyme-worker")
        .withClaim("userId", recipe.userId)
        .withClaim("recipeId",id.toString())
        .withExpiresAt(Instant.now().plusSeconds(15*60))
        .sign(CryptoService.jwtAlgorithm)


        call.response.cookies.append(
            Cookie(
                name="recipe_media_token",
                value=jwt,
                domain=".rosemary-and-thyme.com",
                path="/recipe-images/",
                maxAge = 15*60,
                secure=true,
                httpOnly = true,
                extensions = mapOf("SameSite" to "Strict")
            )
        )
        call.respond(status= HttpStatusCode.OK,message=recipe)
    }
}
