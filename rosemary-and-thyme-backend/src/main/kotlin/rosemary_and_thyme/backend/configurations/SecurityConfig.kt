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

@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val sessionFilter: SessionAuthFilter,
    private val rateLimitFilter: RateLimitFilter,
    @Value($$"${app.frontend-url}") private val frontendUrl: String
) {

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

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .cors { it.configurationSource(corsConfigurationSource()) }
            .csrf { it.disable() } // pazi na napomenu ispod o CSRF-u kod session auth-a
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
