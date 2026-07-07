package rosemary_and_thyme.backend.filters

import io.jsonwebtoken.JwtException
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import rosemary_and_thyme.backend.services.auth.JwtService

@Component
class JwtAuthenticationFilter(
    private val jwtService: JwtService

) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {

        val authHeader = request.getHeader("Authorization")

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response)
            return
        }

        val token = authHeader.removePrefix("Bearer ")

        try {

            val userId =
                jwtService.extractUserId(token)

            val authentication = UsernamePasswordAuthenticationToken(
                userId,
                null,
                emptyList()
            )

            SecurityContextHolder.getContext().authentication = authentication

        } catch (_: JwtException) {
            SecurityContextHolder.clearContext()
        }

        filterChain.doFilter(request, response)
    }
}
