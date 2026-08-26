package rosemary_and_thyme.backend.services.auth

import org.springframework.beans.factory.annotation.Value
import org.springframework.http.ResponseCookie
import org.springframework.stereotype.Component
import java.time.Duration

private const val SESSION_COOKIE_NAME = "session_cookie"
private const val REDIRECT_COOKIE_NAME = "post_login_redirect"

/**
 * Builds every cookie this app hands the browser for auth. Extracted out of
 * [rosemary_and_thyme.backend.controllers.AuthController] (which owned this
 * before) so [rosemary_and_thyme.backend.configurations.GoogleOidcSuccessHandler] -
 * a different class entirely, run by Spring Security's filter chain rather than
 * going through `AuthController` - can build the exact same `session_cookie` a
 * password login gets, plus the new short-lived redirect-preservation cookie the
 * Google flow needs.
 *
 * @param cookieDomain scopes every cookie to this domain, shared between
 * `api.<domain>` and `<domain>` - see `app.cookie-domain` in application.yaml
 */
@Component
class SessionCookieFactory(
    @Value($$"${app.cookie-domain}") private val cookieDomain: String
) {

    /**
     * The real session cookie: `httpOnly` (not readable from JS, mitigates XSS token
     * theft), `Secure` (HTTPS only), `SameSite=Lax`. Still blocks the cookie from
     * being attached to any cross-site POST/PUT/DELETE (the actual mutating requests
     * this app cares about protecting from CSRF), while allowing it on a top-level
     * GET navigation.
     *
     * That last part isn't optional here: this is *also* the cookie
     * [GoogleOidcSuccessHandler][rosemary_and_thyme.backend.configurations.GoogleOidcSuccessHandler]
     * sets on the response that redirects the browser from this app's own backend
     * over to the frontend, at the tail end of a redirect chain that *began* on
     * accounts.google.com. Confirmed empirically (via a live browser's Network tab):
     * browsers keep treating every hop of a redirect chain as cross-site, evidenced
     * by `Sec-Fetch-Site: cross-site` and `Referer: https://accounts.google.com/`
     * still showing up on the *final* hop landing on the frontend - a `SameSite=Strict`
     * cookie set partway through a chain like that is silently never sent on that
     * final request, which is exactly why sign-in was succeeding server-side (visible
     * in the backend's own logs) while the frontend never picked it up: the cookie
     * really was set, but the browser dropped it before it ever reached the page
     * that needed to read it back. Password login never hits this, since that
     * cookie-setting response comes from a same-origin `fetch()` call made by the
     * frontend itself, not a cross-site-originated redirect chain.
     */
    fun session(token: String): ResponseCookie = build(SESSION_COOKIE_NAME, token, Duration.ofDays(30), "Lax")

    /** Clears [session] - same name/domain/path (required for the browser to match and overwrite it), empty value, zero maxAge. */
    fun clearedSession(): ResponseCookie = build(SESSION_COOKIE_NAME, "", Duration.ZERO, "Lax")

    /**
     * Holds a sanitized (see [sanitizeRedirectPath]) "return to this path after
     * login" value across the Google OAuth round trip -
     * [rosemary_and_thyme.backend.controllers.AuthController.startGoogleLogin] sets
     * it right before leaving for Google, [GoogleOidcSuccessHandler][rosemary_and_thyme.backend.configurations.GoogleOidcSuccessHandler]
     * reads and clears it on the way back.
     *
     * `SameSite=Lax`, not `Strict`, deliberately: this cookie has to survive a
     * top-level navigation arriving *from* accounts.google.com back to this app's
     * own domain - a `Strict` cookie is never sent on a cross-site navigation like
     * that at all, `Lax` still allows it for a top-level GET. A short 10-minute
     * `maxAge` is plenty for a real sign-in attempt and limits how long a stale,
     * unused one can linger in the browser.
     */
    fun redirect(path: String): ResponseCookie = build(REDIRECT_COOKIE_NAME, path, Duration.ofMinutes(10), "Lax")

    /** Clears [redirect] once [GoogleOidcSuccessHandler][rosemary_and_thyme.backend.configurations.GoogleOidcSuccessHandler] has read it. */
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
