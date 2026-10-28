package rosemary_and_thyme.modules

import io.ktor.server.application.Application
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import rosemary_and_thyme.routes.auth.authRoutes
import rosemary_and_thyme.routes.recipe.recipeRoutes
import rosemary_and_thyme.routes.user.userRoutes

fun Application.configureRouting() {
    routing {
        get("/") {
            call.respond("Rosemary and Thyme API")
        }

        authenticate("auth-session") {
            userRoutes()
        }
        authRoutes()
        recipeRoutes()
    }
}