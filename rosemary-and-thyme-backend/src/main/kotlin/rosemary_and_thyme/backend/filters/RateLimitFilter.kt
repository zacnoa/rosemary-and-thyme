package rosemary_and_thyme.backend.filters

import io.github.bucket4j.Bandwidth
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import rosemary_and_thyme.backend.services.ratelimit.RateLimiterService
import java.time.Duration

/**
 * Coarse, IP-keyed throttle on the auth endpoints. This is defense-in-depth behind
 * the edge-level (Cloudflare) rate limiting - it doesn't need to be precise per
 * endpoint since the tighter, business-aware limits (per email, e.g. login/resend)
 * live in the services themselves.
 *
 * Runs as a plain servlet Filter, so an exception thrown here would NOT reach
 * GlobalExceptionHandler (that only covers exceptions raised during Spring MVC
 * dispatch) - the 429 response is written directly instead.
 */
@Component
class RateLimitFilter(
    private val rateLimiter: RateLimiterService
) : OncePerRequestFilter() {

    private val ipLimit = Bandwidth.builder().capacity(20).refillIntervally(20, Duration.ofMinutes(1)).build()

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
