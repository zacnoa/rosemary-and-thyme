package rosemary_and_thyme.routes.user

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import rosemary_and_thyme.models.user.UserPrincipal
import rosemary_and_thyme.services.Service
import rosemary_and_thyme.services.user_service.cancelDelete
import rosemary_and_thyme.services.user_service.requestDelete

/**
 * Routes used by the user to manage their account
 */
fun Route.userRoutes() {
    route("/user") {
        /**
         * Endpoint used for getting basic information of a logged in user
         */
        get("/aboutme") {
            //Because this endpoint is behind the authenticate("auth-session") layer we can safely assume that the Principal will never be null
            val userDTO = checkNotNull(call.principal<UserPrincipal>()?.toDTO())

            call.respond(status = HttpStatusCode.OK, message = userDTO)
        }
        /**
         * Endpoint used for posting a delete request of an account
         */
        post("/delete") {
            Service.userService.requestDelete(checkNotNull(call.principal<UserPrincipal>()))
        }
        /**
         * Endpoint used for deleting a deletion request of an account
         */
        delete("/delete") {
            Service.userService.cancelDelete(checkNotNull(call.principal<UserPrincipal>())))
        }
    }
}
