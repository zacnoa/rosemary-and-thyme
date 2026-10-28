package rosemary_and_thyme.routes.auth

import io.ktor.http.HttpStatusCode
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
import rosemary_and_thyme.services.auth_service.createSession
import rosemary_and_thyme.services.auth_service.loginUser

/**
 * Routes used to login/register and manage user credentials
 */
fun Route.authRoutes() {
    route("/auth") {
        /**
         * Endpoint used for registering a user
         */
        post("/register") {
            val request=call.receive<RegisterRequest>()
            Service.authService.registerUser(username = request.username, password = request.password,email=request.email)
            call.respond(status= HttpStatusCode.OK,"You have succesfully signed up")
        }
        /**
         * Endpoint used for verifying a user's email
         */
        post("/verify-email") {}
        /**
         * Endpoint used for resending the verification email
         */
        post("/verify-email/resend") {}
        /**
         * Endpoint used for user login
         */
        post("/login") {
            val request=call.receive<LoginRequest>()
            val userPrincipal=Service.authService.loginUser(request.email,request.password)
            val session=Service.authService.createSession(userPrincipal.id)

            call.sessions.set(UserSession(cookie=session.cookie))
            call.respond(status = HttpStatusCode.OK, message = "You are succesfully logged in")
            

        }
        /**
         * Endpoint used for Google login/registration
         */
        get("/google") {}
        /**
         * Endpoint used for sending the password reset email
         */
        post("/password-reset/request") {}
        /**
         * Endpoint used for processing a password reset
         */
        post("/password-reset/confirm") {}
        /**
         * Endpoint used for logging out a user
         */
        post("/logout") {}
    }
}
