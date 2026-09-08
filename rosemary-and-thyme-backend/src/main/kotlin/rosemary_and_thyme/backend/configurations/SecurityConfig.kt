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

/** Configures the application's CORS and Spring Security filter chains. */
@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val sessionFilter: SessionAuthFilter,
    private val rateLimitFilter: RateLimitFilter,
    private val googleOidcSuccessHandler: GoogleOidcSuccessHandler,
    @Value($$"${app.frontend-url}") private val frontendUrl: String
) {

    /** Configures CORS for frontend requests that include credentials.
     * @return the CORS configuration source.
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

    /** Configures the Google OAuth2/OIDC callback filter chain.
     * @param http the security builder.
     * @return the Google OAuth2 filter chain.
     * @throws Exception if Spring Security cannot build the chain.
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

    /** Configures the stateless API filter chain.
     * @param http the security builder.
     * @return the API filter chain.
     * @throws Exception if Spring Security cannot build the chain.
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
