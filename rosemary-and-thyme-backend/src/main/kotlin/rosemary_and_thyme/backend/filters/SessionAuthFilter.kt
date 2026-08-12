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

/**
 * Authenticates every request from its `session_cookie` cookie, ahead of Spring
 * Security's own authorization checks. Registered in
 * [rosemary_and_thyme.backend.configurations.SecurityConfig.securityFilterChain] as the
 * filter before [org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter] -
 * this is the only auth mechanism the app uses.
 *
 * On success, the resolved [rosemary_and_thyme.backend.jooqgenerated.app_auth.tables.records.UsersRecord]
 * becomes the request's principal, which is how controllers pick it up via
 * `@AuthenticationPrincipal` (see [rosemary_and_thyme.backend.controllers.RecipeController.saveRecipe]).
 */
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
                    user,                                   // principal - the full UsersRecord, so controllers can read it back via @AuthenticationPrincipal
                    null,                                    // credentials - not needed once already authenticated
                    listOf(SimpleGrantedAuthority("ROLE_USER")) // authorities - single flat role; adjust if a real role system is added later
                )
                authentication.details = WebAuthenticationDetailsSource().buildDetails(request)

                SecurityContextHolder.getContext().authentication = authentication
            } catch (ex: SessionException) {
                // Invalid/expired token - just don't set an authentication.
                // Don't throw here; let authorizeHttpRequests decide the outcome (401/403).
                SecurityContextHolder.clearContext()
            }
        }

        filterChain.doFilter(request, response)
    }
}
