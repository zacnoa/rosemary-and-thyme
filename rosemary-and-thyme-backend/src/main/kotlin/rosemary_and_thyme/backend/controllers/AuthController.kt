package rosemary_and_thyme.backend.controllers

import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseCookie
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.CookieValue
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.beans.factory.annotation.Value
import rosemary_and_thyme.backend.exceptions.SessionException
import rosemary_and_thyme.backend.models.LoginRequest
import rosemary_and_thyme.backend.models.RegisterRequest
import rosemary_and_thyme.backend.models.ResendVerificationRequest
import rosemary_and_thyme.backend.models.VerifyEmailRequest
import rosemary_and_thyme.backend.services.auth.AuthService
import java.time.Duration
import java.util.UUID


data class UserDTO(val id: UUID, val username:String, val email:String)

@RestController
@RequestMapping("/auth")
class AuthController(
    private val authService: AuthService,
    @Value($$"${app.cookie-domain}") private val cookieDomain: String
) {



    @PostMapping("/register")
    fun register(@RequestBody request: RegisterRequest): ResponseEntity<Unit> {

        // account is created but stays unusable until the email is verified,
        // so unlike login there is no session cookie to set here
        authService.register(request)

        return ResponseEntity.status(HttpStatus.CREATED).build()

    }

    @PostMapping("/verify-email")
    fun verifyEmail(@RequestBody request: VerifyEmailRequest): ResponseEntity<Unit> {

        val sessionToken = authService.verifyEmail(request.token)
        val sessionCookie = createSessionCookie(sessionToken)

        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, sessionCookie.toString())
            .build()
    }

    @PostMapping("/verify-email/resend")
    fun resendVerificationEmail(@RequestBody request: ResendVerificationRequest): ResponseEntity<Unit> {

        // always 200 regardless of whether the email exists/is already verified -
        // see EmailVerificationService.resend for why
        authService.resendVerificationEmail(request.email)

        return ResponseEntity.ok().build()
    }


    @PostMapping("/login")
    fun login(@RequestBody request: LoginRequest): ResponseEntity<Unit> {

        val sessionToken= authService.login(request.email, request.password)

        val refreshCookie = createSessionCookie(sessionToken)


        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE,
                refreshCookie.toString(),
            )
            .build()
    }


    @PostMapping("/logout")
    fun logout(@CookieValue(value = "session_cookie", required = false) sessionCookie:String?): ResponseEntity<Unit> {

        if(sessionCookie !=null){

            authService.logout(sessionCookie)
        }



        return ResponseEntity.ok()
            .header(
                HttpHeaders.SET_COOKIE,
                clearRefreshCookie().toString()
            )
            .build()
    }

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

    private fun clearRefreshCookie(): ResponseCookie =
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
