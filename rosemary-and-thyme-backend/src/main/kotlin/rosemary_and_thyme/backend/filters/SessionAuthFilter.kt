package rosemary_and_thyme.backend.filters

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import rosemary_and_thyme.backend.exceptions.SessionException
import rosemary_and_thyme.backend.services.auth.AuthService

@Component
class SessionAuthFilter(
    private val authService: AuthService
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val sessionToken = request.cookies
            ?.firstOrNull { it.name == "session_cookie" }
            ?.value

        if (sessionToken != null && SecurityContextHolder.getContext().authentication == null) {
            try {
                val user = authService.validateSession(sessionToken)

                val authentication = UsernamePasswordAuthenticationToken(
                    user,                                   // principal - možeš staviti cijeli UsersRecord ili custom UserDetails
                    null,                                    // credentials - nisu potrebni nakon autentikacije
                    listOf(SimpleGrantedAuthority("ROLE_USER")) // authorities - prilagodi ako imaš role sustav
                )
                authentication.details = WebAuthenticationDetailsSource().buildDetails(request)

                SecurityContextHolder.getContext().authentication = authentication
            } catch (ex: SessionException) {
                // Nevažeći/istekao token - samo ne postavljaj authentication
                // Ne bacaj exception ovdje, pusti da authorizeHttpRequests odluči (401/403)
                SecurityContextHolder.clearContext()
            }
        }

        filterChain.doFilter(request, response)
    }
}
