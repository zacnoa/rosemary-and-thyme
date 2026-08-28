package rosemary_and_thyme.backend.controllers

import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.CookieValue
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import rosemary_and_thyme.backend.models.auth.LoginRequest
import rosemary_and_thyme.backend.models.auth.PasswordResetConfirmRequest
import rosemary_and_thyme.backend.models.auth.PasswordResetRequestRequest
import rosemary_and_thyme.backend.models.auth.RegisterRequest
import rosemary_and_thyme.backend.models.auth.ResendVerificationRequest
import rosemary_and_thyme.backend.models.auth.VerifyEmailRequest
import rosemary_and_thyme.backend.services.auth.AuthService
import rosemary_and_thyme.backend.services.auth.SessionCookieFactory
import rosemary_and_thyme.backend.services.auth.sanitizeRedirectPath


/**
 * Auth endpoints: register -> verify-email -> login -> logout, plus [startGoogleLogin]
 * (Google sign-in's callback is handled separately, by Spring Security's own
 * `oauth2Login()` machinery and [rosemary_and_thyme.backend.configurations.GoogleOidcSuccessHandler] -
 * neither goes through this controller) and the parallel "forgot password" pair,
 * [requestPasswordReset]/[confirmPasswordReset]. Changing your password while already
 * logged in is a separate, session-authenticated endpoint instead -
 * `PUT /user/password`, on [UserController].
 *
 * The session itself is a plain random token (see
 * [rosemary_and_thyme.backend.services.auth.SessionService]) carried by an
 * `httpOnly`/`Secure` cookie ("session_cookie") rather than a bearer header - every
 * endpoint here that establishes or ends a session sets that cookie via [cookies]
 * instead of returning the token in the body. Requests are authenticated from that
 * cookie by [rosemary_and_thyme.backend.filters.SessionAuthFilter].
 */
@RestController
@RequestMapping("/auth")
class AuthController(
    private val authService: AuthService,
    private val cookies: SessionCookieFactory
) {

    /**
     * Creates the account and emails a verification link. Does not log the user in -
     * see [AuthService.register] for why.
     *
     * @param request the new account's username/email/password
     * @return 201 Created with an empty body
     */
    @PostMapping("/register")
    fun register(@RequestBody request: RegisterRequest): ResponseEntity<Unit> {

        // account is created but stays unusable until the email is verified,
        // so unlike login there is no session cookie to set here
        authService.register(request)

        return ResponseEntity.status(HttpStatus.CREATED).build()

    }

    /**
     * Confirms an account's email from the token in the verification link, and logs
     * the user in immediately afterward.
     *
     * @param request the raw verification token from the link
     * @return 200 OK with the new session cookie attached
     */
    @PostMapping("/verify-email")
    fun verifyEmail(@RequestBody request: VerifyEmailRequest): ResponseEntity<Unit> {

        val sessionToken = authService.verifyEmail(request.token)

        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, cookies.session(sessionToken).toString())
            .build()
    }

    /**
     * Re-sends a verification email, e.g. because the first one expired or was lost.
     * Always responds 200 regardless of whether the email is registered or already
     * verified - see [rosemary_and_thyme.backend.services.auth.EmailVerificationService.resend].
     *
     * @param request the email to resend the verification link to
     * @return 200 OK with an empty body
     */
    @PostMapping("/verify-email/resend")
    fun resendVerificationEmail(@RequestBody request: ResendVerificationRequest): ResponseEntity<Unit> {

        // always 200 regardless of whether the email exists/is already verified -
        // see EmailVerificationService.resend for why
        authService.resendVerificationEmail(request.email)

        return ResponseEntity.ok().build()
    }


    /**
     * Logs an existing, verified user in.
     *
     * @param request the account's email/password
     * @return 200 OK with the new session cookie attached
     */
    @PostMapping("/login")
    fun login(@RequestBody request: LoginRequest): ResponseEntity<Unit> {

        val sessionToken = authService.login(request.email, request.password)

        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, cookies.session(sessionToken).toString())
            .build()
    }

    /**
     * Entry point for "Continue with Google" - not itself the OAuth2 redirect (that's
     * Spring Security's own `/oauth2/authorization/google`, auto-configured from the
     * `google` client registration in application.yaml), just a thin hop in front of
     * it that stashes [redirect] in a short-lived cookie first. A plain query param
     * on this URL wouldn't survive the round trip through Google and back on its
     * own - [rosemary_and_thyme.backend.configurations.GoogleOidcSuccessHandler]
     * reads the cookie back once Google's redirected here.
     *
     * @param redirect where to send the browser after a successful Google sign-in,
     * sanitized via [sanitizeRedirectPath] before being trusted with anything
     * @return 302 to Spring Security's own Google authorization endpoint, with the
     * redirect cookie attached
     */
    @GetMapping("/google")
    fun startGoogleLogin(@RequestParam(required = false) redirect: String?): ResponseEntity<Unit> {

        return ResponseEntity.status(HttpStatus.FOUND)
            .header(HttpHeaders.LOCATION, "/oauth2/authorization/google")
            .header(HttpHeaders.SET_COOKIE, cookies.redirect(sanitizeRedirectPath(redirect)).toString())
            .build()
    }


    /**
     * Starts the "forgot password" flow by emailing a reset link. Always responds 200
     * regardless of whether the email is registered, already verified, or belongs to a
     * Google-only account - see [AuthService.requestPasswordReset] /
     * [rosemary_and_thyme.backend.services.auth.PasswordResetService.requestReset].
     *
     * @param request the email to send a reset link to
     * @return 200 OK with an empty body
     */
    @PostMapping("/password-reset/request")
    fun requestPasswordReset(@RequestBody request: PasswordResetRequestRequest): ResponseEntity<Unit> {
        authService.requestPasswordReset(request.email)
        return ResponseEntity.ok().build()
    }

    /**
     * Confirms a password reset from the token in the emailed link, and logs the user
     * in immediately afterward with a fresh session (every other existing session for
     * the account is revoked first - see [AuthService.resetPassword]).
     *
     * @param request the raw reset token plus the new password
     * @return 200 OK with the new session cookie attached
     */
    @PostMapping("/password-reset/confirm")
    fun confirmPasswordReset(@RequestBody request: PasswordResetConfirmRequest): ResponseEntity<Unit> {
        val sessionToken = authService.resetPassword(request.token, request.newPassword)

        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, cookies.session(sessionToken).toString())
            .build()
    }

    /**
     * Ends the current session. A missing/absent cookie is treated the same as a
     * successful logout (nothing to revoke) rather than an error - see
     * [rosemary_and_thyme.backend.services.auth.SessionService.revokeSession].
     *
     * @param sessionCookie the current session token, if any
     * @return 200 OK with the session cookie cleared (empty value, immediate expiry)
     */
    @PostMapping("/logout")
    fun logout(@CookieValue(value = "session_cookie", required = false) sessionCookie:String?): ResponseEntity<Unit> {

        if(sessionCookie !=null){

            authService.logout(sessionCookie)
        }



        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, cookies.clearedSession().toString())
            .build()
    }
}
