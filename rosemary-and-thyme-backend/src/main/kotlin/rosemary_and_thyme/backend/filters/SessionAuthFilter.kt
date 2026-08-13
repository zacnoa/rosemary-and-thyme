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
 *
 * A browser can end up sending *more than one* cookie named `session_cookie` at once -
 * e.g. a stale cookie set under an older `app.cookie-domain` value (see
 * [rosemary_and_thyme.backend.controllers.AuthController.createSessionCookie]) sticking
 * around alongside the current one after that value changes, since the two are distinct
 * cookies (different `Domain` attribute) as far as the browser's cookie jar is concerned
 * even though `HttpServletRequest.getCookies()` reports them with the same name and no
 * `Domain` info to tell them apart by. Picking just the first of those blindly would mean
 * a 50/50 chance of authenticating off the stale, no-longer-valid one and failing the
 * request even though a good session cookie was right there too - so every cookie named
 * `session_cookie` is tried against [AuthService.validateSession] in order, and the first
 * one that actually validates wins.
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
                    // Invalid/expired token on this particular cookie - not necessarily
                    // fatal, another one of the same-named cookies might still be valid.
                    null
                }
            }

            if (user != null) {
                val authentication = UsernamePasswordAuthenticationToken(
                    user,                                   // principal - the full UsersRecord, so controllers can read it back via @AuthenticationPrincipal
                    null,                                    // credentials - not needed once already authenticated
                    listOf(SimpleGrantedAuthority("ROLE_USER")) // authorities - single flat role; adjust if a real role system is added later
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
