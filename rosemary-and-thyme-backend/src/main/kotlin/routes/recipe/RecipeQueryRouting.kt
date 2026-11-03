package rosemary_and_thyme.routes.recipe

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import rosemary_and_thyme.routes.utils.requireUuidPathParameter
import rosemary_and_thyme.services.Service
import rosemary_and_thyme.services.recipe_service.getRecipeById

/** Registers read-only recipe routes. */
fun Route.recipeQueryingRoutes() {
    // Searches for recipes using query parameters.
    get("/search") {
        
    }
    // Returns a recipe identified by a UUID path parameter.
    get("/{id}") {


        val id = call.requireUuidPathParameter("id")
        
        val recipe=Service.recipeService.getRecipeById(id)

        call.respond(status= HttpStatusCode.OK,message=recipe)
    }
}
