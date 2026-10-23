package rosemary_and_thyme.routes.user

import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route

/**
 * Routes used by the user to manage their account
 */
fun Route.userRoutes() {
    route("/user") {
        /**
         * Endpoint used for getting basic information of a logged in user
         */
        get("/aboutme") {}
        /**
         * Endpoint used for posting a delete request of an account
         */
        post("/delete") {}
        /**
         * Endpoint used for deleting a deletion request of an account
         */
        delete("/delete") {}
    }
}
