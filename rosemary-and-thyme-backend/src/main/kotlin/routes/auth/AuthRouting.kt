package rosemary_and_thyme.routes.auth

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import rosemary_and_thyme.models.requests.RegisterRequest
import rosemary_and_thyme.routes.user.userRoutes
import rosemary_and_thyme.services.Service
import rosemary_and_thyme.services.auth_service.registerUser
import io.ktor.server.response.respond
import io.ktor.server.sessions.sessions
import io.ktor.server.sessions.set
import rosemary_and_thyme.models.auth.UserSession
import rosemary_and_thyme.models.requests.LoginRequest
import rosemary_and_thyme.models.user.UserPrincipal
import rosemary_and_thyme.services.auth_service.createSession
import rosemary_and_thyme.services.auth_service.loginUser
import rosemary_and_thyme.services.auth_service.logout

/** Registers public authentication and credential-management routes under `/auth`. */
fun Route.authRoutes() {
    route("/auth") {
        // Registers a user from a RegisterRequest body.
        post("/register") {
            val request = call.receive<RegisterRequest>()
            Service.authService.registerUser(
                username = request.username,
                password = request.password,
                email = request.email
            )
            call.respond(status = HttpStatusCode.OK, "You have succesfully signed up")
        }
        // Verifies a user's email address.
        post("/verify-email") {}
        // Resends the email-verification message.
        post("/verify-email/resend") {}
        // Authenticates a LoginRequest and creates a cookie-backed session.
        post("/login") {
            val request = call.receive<LoginRequest>()
            val userPrincipal = Service.authService.loginUser(request.email, request.password)
            val session = Service.authService.createSession(userPrincipal.id)

            call.sessions.set(UserSession(cookie = session.cookie))
            call.respond(status = HttpStatusCode.OK, message = "You are succesfully logged in")


        }
        // Starts Google authentication or registration.
        get("/google") {}
        // Sends a password-reset message.
        post("/password-reset/request") {}
        // Confirms a password-reset request.
        post("/password-reset/confirm") {}
        // Ends the current user session.
        authenticate("auth-session") {

            post("/logout") {
                val userPrincipal = checkNotNull(call.principal<UserPrincipal>())
                Service.authService.logout(userPrincipal.id)
                call.respond(status = HttpStatusCode.OK, message = "You are succesfully logged out")
            }
        }
    }
}
