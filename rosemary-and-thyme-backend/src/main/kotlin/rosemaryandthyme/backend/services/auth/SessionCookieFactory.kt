package rosemaryandthyme.backend.services.auth

import org.springframework.beans.factory.annotation.Value
import org.springframework.http.ResponseCookie
import org.springframework.stereotype.Component
import java.time.Duration

private const val SESSION_COOKIE_NAME = "session_cookie"
private const val REDIRECT_COOKIE_NAME = "post_login_redirect"

/** Builds authentication and post-login redirect cookies. */
@Component
class SessionCookieFactory(
    @Value($$"${app.cookie-domain}") private val cookieDomain: String
) {

    /** Builds the persistent session cookie.
     * @param token the raw session token.
     * @return the configured session cookie.
     */
    fun session(token: String): ResponseCookie = build(SESSION_COOKIE_NAME, token, Duration.ofDays(30), "Lax")

    /** Builds an expired session cookie.
     * @return a cookie that clears the current session.
     */
    fun clearedSession(): ResponseCookie = build(SESSION_COOKIE_NAME, "", Duration.ZERO, "Lax")

    /** Builds the temporary post-login redirect cookie.
     * @param path the sanitized frontend path to restore after login.
     * @return the configured redirect cookie.
     */
    fun redirect(path: String): ResponseCookie = build(REDIRECT_COOKIE_NAME, path, Duration.ofMinutes(10), "Lax")

    /** Builds an expired post-login redirect cookie.
     * @return a cookie that clears the redirect path.
     */
    fun clearedRedirect(): ResponseCookie = build(REDIRECT_COOKIE_NAME, "", Duration.ZERO, "Lax")

    private fun build(name: String, value: String, maxAge: Duration, sameSite: String): ResponseCookie =
        ResponseCookie.from(name, value)
            .httpOnly(true)
            .secure(true)
            .domain(cookieDomain)
            .path("/")
            .sameSite(sameSite)
            .maxAge(maxAge)
            .build()
}
