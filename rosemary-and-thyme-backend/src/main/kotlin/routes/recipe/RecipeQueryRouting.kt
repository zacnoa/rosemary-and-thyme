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

/** Registers read-only recipe routes. */
fun Route.recipeQueryingRoutes() {
    // Searches for recipes using query parameters.
    get("/search") {
        
    }
    // Returns a recipe identified by a UUID path parameter.
    get("/{id}") {


        val id=call.pathParameters["id"].runCatching { UUID.fromString(this)}
            .getOrElse { throw BadRequestException ("The id of the recipe does not conform to the UUID standard")}
        
        val recipe=Service.recipeService.getRecipeById(id)

        call.respond(status= HttpStatusCode.OK,message=recipe)
    }
}
