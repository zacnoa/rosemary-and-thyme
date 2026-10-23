package rosemary_and_thyme.routes.recipe

import io.ktor.server.routing.Route
import io.ktor.server.routing.route

fun Route.recipeRoutes() {
    route("/recipe") {
        recipeQueryingRoutes()
        recipeManipulationRoutes()
    }
}
