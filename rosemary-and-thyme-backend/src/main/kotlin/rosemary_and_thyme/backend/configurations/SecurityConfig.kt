package rosemary_and_thyme.backend.configurations

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.HttpStatusEntryPoint
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
 * calls in [apiFilterChain].
 *
 * Two separate [SecurityFilterChain] beans, not one, deliberately:
 * [oauth2FilterChain] (`@Order(1)`, scoped via `securityMatcher` to just the Google
 * handshake paths) and [apiFilterChain] (`@Order(2)`, everything else - this app's own
 * stateless session-cookie API). Merely *configuring* `oauth2Login()` anywhere in a
 * chain makes Spring Security auto-install its own entry point (a redirect to
 * `/oauth2/authorization/google`) as that chain's app-wide default for every
 * unauthenticated request - silently turning what every frontend query function's
 * fetch call expects to be a plain JSON 401 (see e.g. `queries/getUser.ts`'s explicit
 * `401`/`403` check) into a redirect chain ending on Google's own HTML sign-in page.
 * An explicit `exceptionHandling.authenticationEntryPoint(...)` in the *same* chain
 * does not reliably override that default (confirmed empirically, contrary to what
 * Spring Security's own docs suggest a single-chain override should do) - keeping
 * `oauth2Login()` confined to its own narrowly-matched chain sidesteps the whole
 * problem instead of fighting it: [apiFilterChain] never configures `oauth2Login()`
 * at all, so its own exception handling is never touched by it in the first place.
 */
@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val sessionFilter: SessionAuthFilter,
    private val rateLimitFilter: RateLimitFilter,
    private val googleOidcSuccessHandler: GoogleOidcSuccessHandler,
    @Value($$"${app.frontend-url}") private val frontendUrl: String
) {

    /**
     * Restricts cross-origin requests to [frontendUrl] only, with credentials
     * (cookies) allowed - required for the `session_cookie` cookie set by
     * [rosemary_and_thyme.backend.controllers.AuthController] to actually be sent by
     * the browser on cross-origin requests from the frontend. Shared by both filter
     * chains below.
     *
     * @return the CORS configuration applied to every path (Spring's catch-all Ant pattern)
     */
    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val configuration = CorsConfiguration()
        configuration.allowedOrigins = listOf(frontendUrl)
        // PATCH: RecipeController.setRecipePrivate (dashboard's private/public toggle)
        configuration.allowedMethods = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
        configuration.allowedHeaders = listOf("*")
        configuration.allowCredentials = true

        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/**", configuration)
        return source
    }

    /**
     * The Google sign-in handshake, and nothing else - `securityMatcher` means every
     * other rule in this chain (including `oauth2Login`'s own auto-installed entry
     * point) simply never applies to any other path. The `oauth2` prefix is where
     * [rosemary_and_thyme.backend.controllers.AuthController.startGoogleLogin] sends
     * the browser to kick the flow off; the `login/oauth2` prefix is Google's callback,
     * handed to [googleOidcSuccessHandler] once Spring Security has validated the ID
     * token itself (signature/issuer/audience/expiry against Google's own JWKS - none
     * of it hand-rolled here).
     *
     * Both paths are `permitAll()` - there's no "authenticated" concept yet at this
     * point, the caller is *becoming* authenticated by going through this flow.
     *
     * @return the configured [SecurityFilterChain] for the Google OAuth2 paths
     */
    @Bean
    @Order(1)
    fun oauth2FilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .securityMatcher("/oauth2/**", "/login/oauth2/**")
            .cors { it.configurationSource(corsConfigurationSource()) }
            .csrf { it.disable() }
            .authorizeHttpRequests { it.anyRequest().permitAll() }
            .oauth2Login {
                it.successHandler(googleOidcSuccessHandler)
            }

        return http.build()
    }

    /**
     * Everything else - this app's own stateless, `session_cookie`-based API:
     * - CSRF disabled - the session cookie is `SameSite=Lax` (see
     *   [rosemary_and_thyme.backend.services.auth.SessionCookieFactory.session] for
     *   why it's `Lax` and not `Strict`), which still blocks it from being attached
     *   to a cross-site POST/PUT/DELETE - a meaningful CSRF mitigation for browsers
     *   that honor it, so the usual double-submit-token CSRF defense is skipped
     * - `SessionCreationPolicy.STATELESS` - tells Spring Security not to use its own
     *   `HttpSession`; auth state for each request comes entirely from
     *   [SessionAuthFilter] validating the `session_cookie` value against the DB, not
     *   from anything Spring Security stores server-side itself
     * - public endpoints: login/register/verify-email(+resend)/google/password-reset(request+confirm), `GET /recipe/{id}`,
     *   and `GET /recipe/search` (`/auth/google` itself just sets a cookie before
     *   302-ing into the *other* chain's `/oauth2/authorization/google`;
     *   `RecipeController.searchRecipes` never returns anything but id/name pairs, no
     *   private data); everything else requires authentication
     * - `exceptionHandling.authenticationEntryPoint` pinned to a plain 401 - see this
     *   class's own KDoc for why that needs its own dedicated chain rather than living
     *   alongside `oauth2Login()`
     * - filter order: [rateLimitFilter] before [sessionFilter] before Spring Security's
     *   own `UsernamePasswordAuthenticationFilter` - rate limiting rejects abusive
     *   requests before any session validation work happens, and session validation
     *   happens before Spring Security's built-in filter so `@AuthenticationPrincipal`
     *   is already populated by the time authorization decisions are made
     *
     * @return the configured [SecurityFilterChain] for every other path
     */
    @Bean
    @Order(2)
    fun apiFilterChain(http: HttpSecurity): SecurityFilterChain {
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
                    "/auth/verify-email/resend",
                    "/auth/google",
                    "/auth/password-reset/request",
                    "/auth/password-reset/confirm"
                ).permitAll()
                it.requestMatchers(HttpMethod.GET, "/recipe/{id}").permitAll()
                it.requestMatchers(HttpMethod.GET, "/recipe/search").permitAll()
                it.anyRequest().authenticated()
            }
            .exceptionHandling {
                it.authenticationEntryPoint(HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
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
