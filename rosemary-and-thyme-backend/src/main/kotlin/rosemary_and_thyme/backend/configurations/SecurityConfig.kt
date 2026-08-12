package rosemary_and_thyme.backend.configurations

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource
import rosemary_and_thyme.backend.filters.RateLimitFilter
import rosemary_and_thyme.backend.filters.SessionAuthFilter

/**
 * Wires up CORS, CSRF, and the request pipeline's two custom filters
 * ([SessionAuthFilter], [RateLimitFilter]). This is the single source of truth for
 * which endpoints require authentication - see the `requestMatchers(...).permitAll()`
 * calls in [securityFilterChain].
 */
@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val sessionFilter: SessionAuthFilter,
    private val rateLimitFilter: RateLimitFilter,
    @Value($$"${app.frontend-url}") private val frontendUrl: String
) {

    /**
     * Restricts cross-origin requests to [frontendUrl] only, with credentials
     * (cookies) allowed - required for the `session_cookie` cookie set by
     * [rosemary_and_thyme.backend.controllers.AuthController] to actually be sent by
     * the browser on cross-origin requests from the frontend.
     *
     * @return the CORS configuration applied to every path (Spring's catch-all Ant pattern)
     */
    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val configuration = CorsConfiguration()
        configuration.allowedOrigins = listOf(frontendUrl)
        configuration.allowedMethods = listOf("GET", "POST", "PUT", "DELETE", "OPTIONS")
        configuration.allowedHeaders = listOf("*")
        configuration.allowCredentials = true

        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/**", configuration)
        return source
    }

    /**
     * Builds the security filter chain:
     * - CORS from [corsConfigurationSource]
     * - CSRF disabled - the session cookie is `SameSite=Strict` (see
     *   [rosemary_and_thyme.backend.controllers.AuthController.createSessionCookie]),
     *   which is itself a strong CSRF mitigation for browsers that honor it, so the
     *   usual double-submit-token CSRF defense is skipped
     * - `SessionCreationPolicy.STATELESS` - tells Spring Security not to use its own
     *   `HttpSession`; auth state for each request comes entirely from
     *   [SessionAuthFilter] validating the `session_cookie` value against the DB, not
     *   from anything Spring Security stores server-side itself
     * - public endpoints: login/register/verify-email(+resend), and `GET /recipe/{id}`
     *   (recipes are publicly viewable); everything else requires authentication
     * - filter order: [rateLimitFilter] before [sessionFilter] before Spring Security's
     *   own `UsernamePasswordAuthenticationFilter` - rate limiting rejects abusive
     *   requests before any session validation work happens, and session validation
     *   happens before Spring Security's built-in filter so `@AuthenticationPrincipal`
     *   is already populated by the time authorization decisions are made
     *
     * @return the configured [SecurityFilterChain]
     */
    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .cors { it.configurationSource(corsConfigurationSource()) }
            .csrf { it.disable() } // see the CSRF note in this function's KDoc above
            .sessionManagement {
                it.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            }
            .authorizeHttpRequests {
                it.requestMatchers(
                    "/auth/login",
                    "/auth/register",
                    "/auth/verify-email",
                    "/auth/verify-email/resend"
                ).permitAll()
                it.requestMatchers(HttpMethod.GET, "/recipe/{id}").permitAll()
                it.anyRequest().authenticated()
            }
            .addFilterBefore(
                sessionFilter,
                UsernamePasswordAuthenticationFilter::class.java
            )
            .addFilterBefore(
                rateLimitFilter,
                SessionAuthFilter::class.java
            )

        return http.build()
    }
}
