package rosemary_and_thyme.routes.recipe

import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.put

/** Registers routes that create, update, and delete recipe data. */
fun Route.recipeManipulationRoutes() {
    // Creates a recipe.
    post { }
    // Replaces an existing recipe.
    put("/{id}") {}
    // Creates a like for a recipe.
    post("/{id}/like") {}
    // Removes the current user's like from a recipe.
    delete("/{id}/like") {}
    // Records that a recipe was made.
    post("/{id}/made") {}
    // Deletes a recipe.
    delete("/{id}") {}
    // Toggles a recipe's private flag.
    patch("/{id}/private") {}
}
