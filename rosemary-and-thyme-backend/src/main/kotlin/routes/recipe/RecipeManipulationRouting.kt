package rosemary_and_thyme.routes.recipe

import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.put

/**
 * Routes used for post/put/patch requests on recipes
 */
fun Route.recipeManipulationRoutes() {
    /**
     * Endpoint used for posting a new recipe.
     */
    post { }
    /**
     * Endpoint used for updating an existing recipe
     */
    put("/{id}") {}
    /**
     * Endpoint used for creating a like entry
     */
    post("/{id}/like") {}
    /**
     * Endpoint used for deleting a liked entry
     */
    delete("/{id}/like") {}
    /**
     * Endpoint used for updating the made counter for recipe
     */
    post("/{id}/made") {}
    /**
     * Endpoint used for deleting a recipe
     */
    delete("/{id}") {}
    /**
     * Endpoint used for toggling the private flag of a recipe
     */
    patch("/{id}/private") {}
}
