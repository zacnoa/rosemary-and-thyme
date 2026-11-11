package rosemary_and_thyme.routes.recipe

import com.auth0.jwt.JWT
import io.ktor.http.Cookie
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import rosemary_and_thyme.routes.utils.requireUuidPathParameter
import rosemary_and_thyme.services.CryptoService
import rosemary_and_thyme.services.Service
import rosemary_and_thyme.services.recipe_service.getRecipeById
import java.time.Instant

/**
 * Registers read-only recipe routes.
 * Recipe responses issue a short-lived HTTP-only JWT cookie for media access through the worker.
 */
fun Route.recipeQueryingRoutes() {
    // Searches for recipes using query parameters.
    get("/search") {
        
    }
    // Returns a recipe identified by a UUID path parameter.
    get("/{id}") {


        val id = call.requireUuidPathParameter("id")
        
        val recipe=Service.recipeService.getRecipeById(id)
        val jwt= JWT.create()
        .withIssuer("rosemary-and-thyme")
        .withAudience("rosemary-and-thyme-worker")
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
