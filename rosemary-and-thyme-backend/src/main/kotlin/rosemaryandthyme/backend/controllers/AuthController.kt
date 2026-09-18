package rosemaryandthyme.backend.controllers

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
import rosemaryandthyme.backend.models.auth.LoginRequest
import rosemaryandthyme.backend.models.auth.PasswordResetConfirmRequest
import rosemaryandthyme.backend.models.auth.PasswordResetRequestRequest
import rosemaryandthyme.backend.models.auth.RegisterRequest
import rosemaryandthyme.backend.models.auth.ResendVerificationRequest
import rosemaryandthyme.backend.models.auth.VerifyEmailRequest
import rosemaryandthyme.backend.services.auth.AuthService
import rosemaryandthyme.backend.services.auth.SessionCookieFactory
import rosemaryandthyme.backend.services.auth.sanitizeRedirectPath


/** Exposes registration, login, logout, verification, password-reset, and Google-login endpoints. */
@RestController
@RequestMapping("/auth")
class AuthController(
    private val authService: AuthService,
    private val cookies: SessionCookieFactory
) {

    /** Registers an account and sends its verification email.
     * @param request the registration details.
     * @return `201 Created` after registration.
     */
    @PostMapping("/register")
    fun register(@RequestBody request: RegisterRequest): ResponseEntity<Unit> {

        // account is created but stays unusable until the email is verified,
        // so unlike login there is no session cookie to set here
        authService.register(request)

        return ResponseEntity.status(HttpStatus.CREATED).build()

    }

    /** Verifies an email token and sets a session cookie.
     * @param request the verification token.
     * @return `200 OK` with a new session cookie.
     */
    @PostMapping("/verify-email")
    fun verifyEmail(@RequestBody request: VerifyEmailRequest): ResponseEntity<Unit> {

        val sessionToken = authService.verifyEmail(request.token)

        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, cookies.session(sessionToken).toString())
            .build()
    }

    /** Requests another verification email.
     * @param request the account email.
     * @return `200 OK` whether or not an email is sent.
     */
    @PostMapping("/verify-email/resend")
    fun resendVerificationEmail(@RequestBody request: ResendVerificationRequest): ResponseEntity<Unit> {

        // always 200 regardless of whether the email exists/is already verified -
        // see EmailVerificationService.resend for why
        authService.resendVerificationEmail(request.email)

        return ResponseEntity.ok().build()
    }


    /** Logs in a verified password-based account.
     * @param request the email and password.
     * @return `200 OK` with a new session cookie.
     */
    @PostMapping("/login")
    fun login(@RequestBody request: LoginRequest): ResponseEntity<Unit> {

        val sessionToken = authService.login(request.email, request.password)

        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, cookies.session(sessionToken).toString())
            .build()
    }

    /** Begins the Google OAuth2 login flow.
     * @param redirect the frontend path to restore after login.
     * @return `302 Found` to Spring Security's Google authorization endpoint.
     */
    @GetMapping("/google")
    fun startGoogleLogin(@RequestParam(required = false) redirect: String?): ResponseEntity<Unit> {

        return ResponseEntity.status(HttpStatus.FOUND)
            .header(HttpHeaders.LOCATION, "/oauth2/authorization/google")
            .header(HttpHeaders.SET_COOKIE, cookies.redirect(sanitizeRedirectPath(redirect)).toString())
            .build()
    }


    /** Requests a password-reset email.
     * @param request the account email.
     * @return `200 OK` whether or not an email is sent.
     */
    @PostMapping("/password-reset/request")
    fun requestPasswordReset(@RequestBody request: PasswordResetRequestRequest): ResponseEntity<Unit> {
        authService.requestPasswordReset(request.email)
        return ResponseEntity.ok().build()
    }

    /** Resets a password and sets a fresh session cookie.
     * @param request the reset token and replacement password.
     * @return `200 OK` with a new session cookie.
     */
    @PostMapping("/password-reset/confirm")
    fun confirmPasswordReset(@RequestBody request: PasswordResetConfirmRequest): ResponseEntity<Unit> {
        val sessionToken = authService.resetPassword(request.token, request.newPassword)

        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, cookies.session(sessionToken).toString())
            .build()
    }

    /** Logs out the current session.
     * @param sessionCookie the optional session cookie.
     * @return `200 OK` with an expired session cookie.
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
