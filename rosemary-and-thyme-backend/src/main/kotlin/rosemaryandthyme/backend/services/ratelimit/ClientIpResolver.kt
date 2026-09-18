package rosemaryandthyme.backend.services.ratelimit

import jakarta.servlet.http.HttpServletRequest
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/** Resolves the client address used by IP-scoped rate limits. */
@Component
class ClientIpResolver(
    @Value($$"${app.trust-cloudflare-connecting-ip:false}")
    private val trustCloudflareConnectingIp: Boolean
) {
    private val ipLiteral = Regex("^[0-9a-fA-F:.]{2,45}$")

    /** Uses Cloudflare's address only when the deployment explicitly trusts that proxy header. */
    fun resolve(request: HttpServletRequest): String {
        if (trustCloudflareConnectingIp) {
            request.getHeader("CF-Connecting-IP")
                ?.trim()
                ?.takeIf { it.matches(ipLiteral) }
                ?.let { return it }
        }

        return request.remoteAddr
    }
}
