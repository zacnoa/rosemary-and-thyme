package rosemary_and_thyme.routes.recipe

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.util.reflect.TypeInfo
import rosemary_and_thyme.services.Service
import rosemary_and_thyme.services.recipe_service.getRecipeById
import java.util.UUID

/**
 * Routes used for get requests for recipes
 */
fun Route.recipeQueryingRoutes() {
    /**
     * Endpoint for searching for a recipe using a set of parameters
     */
    get("/search") {
        
    }
    /**
     * Endpoint for getting a recipe by its id
     */
    get("/{id}") {

        TODO("Hadndle invalid UUID parameter")
        TODO("Impelment RFC 9457")
        val id=call.pathParameters["id"]
        val recipe=Service.recipeService.getRecipeById(UUID.fromString(id))

        call.response.status(HttpStatusCode.OK)
        call.respond( recipe)
    }
}
