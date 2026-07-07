package rosemary_and_thyme.backend.controllers

import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseCookie
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.CookieValue
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import rosemary_and_thyme.backend.models.LoginRequest
import rosemary_and_thyme.backend.models.RegisterRequest
import rosemary_and_thyme.backend.services.auth.AuthService
import java.time.Duration


data class JwtResponse(val token: String)

@RestController
@RequestMapping("/auth")
class AuthController(
    private val authService: AuthService
) {

    @PostMapping("/register")
    fun register(@RequestBody request: RegisterRequest): ResponseEntity<JwtResponse> {
        val jwtRefreshPair = authService.register(request)

        val refreshCookie = createRefreshCookie(jwtRefreshPair.refreshToken)
        val token = JwtResponse(jwtRefreshPair.accessToken)

        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
            .body(token)

    }


    @PostMapping("/login")
    fun login(@RequestBody request: LoginRequest): ResponseEntity<JwtResponse> {

        val jwtRefreshPair = authService.login(request.email, request.password)

        val refreshCookie = createRefreshCookie(jwtRefreshPair.refreshToken)

        val token = JwtResponse(jwtRefreshPair.accessToken)

        return ResponseEntity.ok()
            .header(
                HttpHeaders.SET_COOKIE,
                refreshCookie.toString(),
            )
            .body(token)
    }

    @PostMapping("/refresh")
    fun refresh(@CookieValue("refresh_token") refreshToken: String): ResponseEntity<JwtResponse> {

        val jwtRefreshPair =
            authService.refresh(refreshToken)

        val refreshCookie = createRefreshCookie(jwtRefreshPair.refreshToken)

        val token = JwtResponse(jwtRefreshPair.accessToken)

        return ResponseEntity.ok()
            .header(
                HttpHeaders.SET_COOKIE,
                refreshCookie.toString()
            )
            .body(token)
    }

    @PostMapping("/logout")
    fun logout(
        @CookieValue(
            value = "refresh_token",
            required = false
        )
        refreshToken: String?
    ): ResponseEntity<Unit> {

        if (refreshToken != null) {
            authService.logout(refreshToken)
        }

        return ResponseEntity.noContent()
            .header(
                HttpHeaders.SET_COOKIE,
                clearRefreshCookie().toString()
            )
            .build()
    }

    //TODO: dont forget to set secure to TRUE
    private fun createRefreshCookie(refreshToken: String): ResponseCookie =
        ResponseCookie.from(
            "refresh_token",
            refreshToken
        )
            .httpOnly(true)
            .secure(false)
            .path("/auth")
            .sameSite("Strict")
            .maxAge(Duration.ofDays(30))
            .build()

    private fun clearRefreshCookie(): ResponseCookie =
        ResponseCookie.from(
            "refresh_token",
            ""
        )
            .httpOnly(true)
            .secure(false)
            .path("/auth")
            .sameSite("Strict")
            .maxAge(Duration.ZERO)
            .build()
}
