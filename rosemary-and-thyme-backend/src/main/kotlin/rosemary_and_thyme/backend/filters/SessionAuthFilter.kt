package rosemary_and_thyme.backend.filters

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import rosemary_and_thyme.backend.exceptions.SessionException
import rosemary_and_thyme.backend.services.auth.AuthService
import rosemary_and_thyme.backend.services.auth.hashToken

/** Authenticates requests from valid session cookies before authorization checks. */
@Component
class SessionAuthFilter(
    private val authService: AuthService
) : OncePerRequestFilter() {
    private val log = LoggerFactory.getLogger(SessionAuthFilter::class.java)

    /** Resolves a session cookie into the Spring Security principal.
     * @param request the incoming HTTP request.
     * @param response the HTTP response.
     * @param filterChain the remaining servlet filters.
     */
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val sessionCookies = request.cookies
            ?.filter { it.name == "session_cookie" }
            ?: emptyList()

        if (sessionCookies.isNotEmpty() && SecurityContextHolder.getContext().authentication == null) {
            // Try each same-named cookie in turn - see the class KDoc above for why
            // there can be more than one - and stop at the first one that validates.
            val user = sessionCookies.firstNotNullOfOrNull { cookie ->
                try {
                    authService.validateSession(cookie.value)
                } catch (ex: SessionException) {
                    log.warn(
                        "Rejected session cookie for {} {}: {} (hash prefix: {})",
                        request.method,
                        request.requestURI,
                        ex.message,
                        hashToken(cookie.value).take(12)
                    )
                    // Invalid/expired token on this particular cookie - not necessarily
                    // fatal, another one of the same-named cookies might still be valid.
                    null
                }
            }

            if (user != null) {
                val authentication = UsernamePasswordAuthenticationToken(
                    user,                                   // principal - the full UsersRecord, so controllers can read it back via @AuthenticationPrincipal
                    null,                                    // credentials - not needed once already authenticated
                )
                authentication.details = WebAuthenticationDetailsSource().buildDetails(request)

                SecurityContextHolder.getContext().authentication = authentication
            } else {
                // None of the session_cookie values (there was at least one) validated -
                // don't throw here; let authorizeHttpRequests decide the outcome (401/403).
                SecurityContextHolder.clearContext()
            }
        }

        filterChain.doFilter(request, response)
    }
}
