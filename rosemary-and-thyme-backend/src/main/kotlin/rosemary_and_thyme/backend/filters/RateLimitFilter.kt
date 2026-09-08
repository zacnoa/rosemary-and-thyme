package rosemary_and_thyme.backend.filters

import io.github.bucket4j.Bandwidth
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import rosemary_and_thyme.backend.services.ratelimit.RateLimiterService
import java.time.Duration

/** Applies an IP-based rate limit to authentication requests. */
@Component
class RateLimitFilter(
    private val rateLimiter: RateLimiterService
) : OncePerRequestFilter() {

    private val ipLimit = Bandwidth.builder().capacity(20).refillIntervally(20, Duration.ofMinutes(1)).build()

    /** Applies the auth IP limit before continuing the filter chain.
     * @param request the incoming HTTP request.
     * @param response the HTTP response.
     * @param filterChain the remaining servlet filters.
     */
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        if (!request.requestURI.startsWith("/auth")) {
            filterChain.doFilter(request, response)
            return
        }

        val ip = request.getHeader("CF-Connecting-IP") ?: request.remoteAddr

        if (!rateLimiter.tryConsume("auth-ip", ip, ipLimit)) {
            response.status = 429 // HttpServletResponse has no SC_TOO_MANY_REQUESTS constant (RFC 6585, added after the servlet spec)
            response.contentType = "application/problem+json"
            response.writer.write(
                """{"type":"about:blank","title":"Too many requests","status":429,"detail":"Too many requests from this address, please try again later.","instance":"${request.requestURI}"}"""
            )
            return
        }

        filterChain.doFilter(request, response)
    }
}
