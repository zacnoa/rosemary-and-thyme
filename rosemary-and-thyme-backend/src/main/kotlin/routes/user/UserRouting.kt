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
import rosemary_and_thyme.services.user_service.requestUserDelete
import rosemary_and_thyme.services.auth_service.requestPasswordReset
import rosemary_and_thyme.services.email_service.sendPasswordResetEmail
import rosemary_and_thyme.services.user_service.cancelUserDelete

/** Registers authenticated account-management routes under `/user`. */
fun Route.userRoutes() {
    route("/user") {
        // Requests a reset for the authenticated account; the client cannot override the email.
        post("/password-reset/request") {
            val principal = checkNotNull(call.principal<UserPrincipal>())
            val delivery = Service.authService.requestPasswordReset(principal.email)
            Service.emailService.sendPasswordResetEmail(delivery)
            call.respond(HttpStatusCode.OK)
        }
        // Returns public account data from the authenticated principal.
        get("/aboutme") {
            val userDTO = checkNotNull(call.principal<UserPrincipal>()?.toDTO())

            call.respond(status = HttpStatusCode.OK, message = userDTO)
        }
        // Schedules the authenticated account for deletion after 30 days.
        post("/delete") {
            Service.userService.requestUserDelete(checkNotNull(call.principal<UserPrincipal>()))
        }
        // Cancels the authenticated account's pending deletion.
        delete("/delete") {
            Service.userService.cancelUserDelete(checkNotNull(call.principal<UserPrincipal>()))
        }
    }
}
