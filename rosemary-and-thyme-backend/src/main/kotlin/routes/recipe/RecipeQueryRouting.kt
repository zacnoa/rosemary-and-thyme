package rosemary_and_thyme.routes.recipe

import io.ktor.http.HttpStatusCode
import io.ktor.server.plugins.BadRequestException
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


        val id=call.pathParameters["id"].runCatching { UUID.fromString(this)}
            .getOrElse { throw BadRequestException ("The id of the recipe does not conform to the UUID standard")}
        
        val recipe=Service.recipeService.getRecipeById(id)

        call.respond(status= HttpStatusCode.OK,recipe)
    }
}
