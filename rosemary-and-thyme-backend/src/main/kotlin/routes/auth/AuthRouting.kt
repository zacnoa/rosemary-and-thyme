package rosemary_and_thyme.routes.auth

import com.resend.core.exception.ResendException
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import rosemary_and_thyme.models.requests.RegisterRequest
import rosemary_and_thyme.services.Service
import rosemary_and_thyme.services.auth_service.registerUser
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.sessions.sessions
import io.ktor.server.sessions.set
import rosemary_and_thyme.models.requests.LoginRequest
import rosemary_and_thyme.models.requests.PasswordResetRequest
import rosemary_and_thyme.models.requests.ConfirmPasswordResetRequest
import rosemary_and_thyme.models.requests.ResendEmailVerificationRequest
import rosemary_and_thyme.models.requests.ConfirmEmailVerificationRequest
import rosemary_and_thyme.models.responses.EmailVerificationStartedResponse
import rosemary_and_thyme.models.exceptions.ResourceNotFoundException
import rosemary_and_thyme.services.auth_service.resendEmailVerification
import rosemary_and_thyme.services.auth_service.confirmEmailVerification
import rosemary_and_thyme.services.auth_service.requestPasswordReset
import rosemary_and_thyme.services.auth_service.confirmPasswordReset
import rosemary_and_thyme.models.user.UserPrincipal
import rosemary_and_thyme.services.auth_service.loginUser
import rosemary_and_thyme.services.auth_service.logoutUser
import rosemary_and_thyme.services.email_service.sendVerificationEmail
import rosemary_and_thyme.services.email_service.sendPasswordResetEmail

/** Registers public authentication and credential-management routes under `/auth`. */
fun Route.authRoutes() {
    route("/auth") {
        // Registers a user from a RegisterRequest body.
        post("/register") {
            val request = call.receive<RegisterRequest>()
            val delivery = Service.authService.registerUser(
                username = request.username,
                password = request.password,
                email = request.email
            )
            val emailSent = runCatching { Service.emailService.sendVerificationEmail(delivery) }
                .onFailure { error ->
                    if (error !is ResendException) throw error
                }
                .isSuccess

            call.response.header(HttpHeaders.CacheControl, "no-store")
            call.respond(HttpStatusCode.OK, EmailVerificationStartedResponse(delivery.verificationId, emailSent))
        }
        // Verifies a user's email address.
        post("/verify-email") {
            val request = call.receive<ConfirmEmailVerificationRequest>()
            val session = Service.authService.confirmEmailVerification(request.verificationId, request.code)
            call.sessions.set(session)
            call.respond(HttpStatusCode.OK)
        }
        // Resends the email-verification message.
        post("/verify-email/resend") {
            val request = call.receive<ResendEmailVerificationRequest>()
            val delivery = Service.authService.resendEmailVerification(request.verificationId)
            Service.emailService.sendVerificationEmail(delivery)
            call.respond(HttpStatusCode.OK)
        }
        // Authenticates a LoginRequest and creates a cookie-backed session.
        post("/login") {
            val request = call.receive<LoginRequest>()
            val session = Service.authService.loginUser(request.email, request.password)
            call.sessions.set(session)
            call.respond(status = HttpStatusCode.OK, message = "You are succesfully logged in")


        }
        // Starts Google authentication or registration.
        get("/google") {
            call.respondRedirect ( "/oidc/google/login" )
        }
        // Returns the same public response for missing accounts and accepted reset requests.
        post("/password-reset/request") {
            val request = call.receive<PasswordResetRequest>()

            runCatching {
                val delivery = Service.authService.requestPasswordReset(request.email)
                Service.emailService.sendPasswordResetEmail(delivery)
            }.onFailure { error ->
                if (error !is ResourceNotFoundException && error !is ResendException) {
                    throw error
                }
            }

            call.respond(
                HttpStatusCode.OK,
                "If an account exists for this email, password reset instructions will be sent."
            )
        }
        // Confirms a password-reset request.
        post("/password-reset/confirm") {
            val request = call.receive<ConfirmPasswordResetRequest>()
            val session = Service.authService.confirmPasswordReset(request.token, request.newPassword)
            call.sessions.set(session)
            call.respond(HttpStatusCode.OK)
        }
        // Ends the current user session.
        authenticate("auth-session") {

            post("/logout") {
                val userPrincipal = checkNotNull(call.principal<UserPrincipal>())
                Service.authService.logoutUser(userPrincipal.id)
                call.respond(status = HttpStatusCode.OK, message = "You are succesfully logged out")
            }
        }
    }
}
