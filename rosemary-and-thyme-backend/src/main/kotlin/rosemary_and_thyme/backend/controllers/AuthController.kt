package rosemary_and_thyme.backend.controllers

import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseCookie
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.CookieValue
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import rosemary_and_thyme.backend.exceptions.SessionException
import rosemary_and_thyme.backend.models.LoginRequest
import rosemary_and_thyme.backend.models.RegisterRequest
import rosemary_and_thyme.backend.services.auth.AuthService
import java.time.Duration
import java.util.UUID


data class JwtResponse(val token: String)
data class UserDTO(val id: UUID, val username:String, val email:String)

@RestController
@RequestMapping("/auth")
class AuthController(
    private val authService: AuthService
) {


    @GetMapping("/aboutme")
    fun aboutMe(@CookieValue(name="session_cookie", required = false) sessionToken:String?):ResponseEntity<UserDTO>{

        if(sessionToken ==null) {
            throw SessionException("User is not logged in")
        }

        val user=authService.validateSession(sessionToken)

        return ResponseEntity.ok().body(UserDTO(user.id!!,user.username,user.email!!))

    }

    @PostMapping("/register")
    fun register(@RequestBody request: RegisterRequest): ResponseEntity<Unit> {

        val sessionToken=authService.register(request)
        val sessionCookie=createSessionCookie(sessionToken)

        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, sessionCookie.toString())
            .build()

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

    //TODO: dont forget to set secure to TRUE
    private fun createSessionCookie(sessionToken: String): ResponseCookie =
        ResponseCookie.from(
            "session_cookie",
            sessionToken
        )
            .httpOnly(true)
            .secure(false)
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
            .secure(false)
            .path("/")
            .sameSite("Strict")
            .maxAge(Duration.ZERO)
            .build()
}
