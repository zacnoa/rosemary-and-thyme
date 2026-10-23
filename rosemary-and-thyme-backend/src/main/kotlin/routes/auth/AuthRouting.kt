package rosemary_and_thyme.routes.auth

import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route

/**
 * Routes used to login/register and manage user credentials
 */
fun Route.authRoutes() {
    route("/auth") {
        /**
         * Endpoint used for registering a user
         */
        post("/register") {}
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
        post("/login") {}
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
