package rosemary_and_thyme.backend.controllers

import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseCookie
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.CookieValue
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.beans.factory.annotation.Value
import rosemary_and_thyme.backend.models.auth.LoginRequest
import rosemary_and_thyme.backend.models.auth.RegisterRequest
import rosemary_and_thyme.backend.models.auth.ResendVerificationRequest
import rosemary_and_thyme.backend.models.auth.VerifyEmailRequest
import rosemary_and_thyme.backend.services.auth.AuthService
import java.time.Duration


/**
 * Auth endpoints: register -> verify-email -> login -> logout.
 *
 * The session itself is a plain random token (see
 * [rosemary_and_thyme.backend.services.auth.SessionService]) carried by an
 * `httpOnly`/`Secure` cookie ("session_cookie") rather than a bearer header - every
 * endpoint here that establishes or ends a session sets that cookie via
 * [createSessionCookie]/[clearSessionCookie] instead of returning the token in the body.
 * Requests are authenticated from that cookie by
 * [rosemary_and_thyme.backend.filters.SessionAuthFilter].
 */
@RestController
@RequestMapping("/auth")
class AuthController(
    private val authService: AuthService,
    @Value($$"${app.cookie-domain}") private val cookieDomain: String
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
        val sessionCookie = createSessionCookie(sessionToken)

        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, sessionCookie.toString())
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

        val sessionToken= authService.login(request.email, request.password)

        val sessionCookie = createSessionCookie(sessionToken)


        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE,
                sessionCookie.toString(),
            )
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
            .header(
                HttpHeaders.SET_COOKIE,
                clearSessionCookie().toString()
            )
            .build()
    }

    /**
     * Builds the `Set-Cookie` header value that hands a session token to the browser:
     * `httpOnly` (not readable from JS, mitigates XSS token theft), `Secure` (HTTPS
     * only), `SameSite=Strict` (not sent on cross-site requests, mitigates CSRF), and
     * scoped to [cookieDomain] with a 30-day expiry matching the session TTL.
     *
     * @param sessionToken the raw session token to store in the cookie
     * @return a `session_cookie` [ResponseCookie] ready to attach as a `Set-Cookie` header
     */
    private fun createSessionCookie(sessionToken: String): ResponseCookie =
        ResponseCookie.from(
            "session_cookie",
            sessionToken
        )
            .httpOnly(true)
            .secure(true)
            .domain(cookieDomain)
            .path("/")
            .sameSite("Strict")
            .maxAge(Duration.ofDays(30))
            .build()

    /**
     * Builds the `Set-Cookie` header value that clears the session cookie on logout:
     * same name/domain/path as [createSessionCookie] (required for the browser to match
     * and overwrite it) but an empty value and `maxAge` of zero, which tells the browser
     * to delete the cookie immediately.
     *
     * @return a `session_cookie` [ResponseCookie] that deletes the cookie client-side
     */
    private fun clearSessionCookie(): ResponseCookie =
        ResponseCookie.from(
            "session_cookie",
            ""
        )
            .httpOnly(true)
            .secure(true)
            .domain(cookieDomain)
            .path("/")
            .sameSite("Strict")
            .maxAge(Duration.ZERO)
            .build()
}
