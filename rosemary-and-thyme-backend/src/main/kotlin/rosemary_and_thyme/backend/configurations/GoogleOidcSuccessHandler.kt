package rosemary_and_thyme.backend.configurations

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.security.web.authentication.AuthenticationSuccessHandler
import org.springframework.stereotype.Component
import rosemary_and_thyme.backend.repositories.dbNotNull
import rosemary_and_thyme.backend.services.auth.GoogleAuthService
import rosemary_and_thyme.backend.services.auth.SessionCookieFactory
import rosemary_and_thyme.backend.services.auth.SessionService
import rosemary_and_thyme.backend.services.auth.sanitizeRedirectPath

/**
 * Runs once Spring Security has finished the Google OAuth2/OIDC handshake itself -
 * authorization code exchange, ID token signature/issuer/audience/expiry validation
 * against Google's own JWKS - all handled by `oauth2Login()` (see [SecurityConfig]),
 * none of it hand-rolled here. From this point on it's just "translate a verified
 * Google identity into one of our own accounts and log them in the same way password
 * login does" - see [GoogleAuthService] for the account lookup/link/create decision,
 * and [SessionService.createSession]/[SessionCookieFactory.session] for the exact
 * same session mechanism [rosemary_and_thyme.backend.controllers.AuthController] uses
 * for a password login.
 *
 * `SessionCreationPolicy.STATELESS` (see [SecurityConfig]) means nothing about this
 * `Authentication` persists past this one request - Spring Security's OAuth2 login
 * filter hands it to this handler and that's the end of its life as far as this app's
 * own auth state is concerned; everything from here on is our own `session_cookie`.
 *
 * Note: Spring Security's *default* `OAuth2AuthorizationRequestRepository` (which
 * holds the in-flight `state`/PKCE verifier between the redirect out to Google and
 * the callback back here) still relies on a container `HttpSession` for that one
 * round trip, independent of this app's own stateless session_cookie mechanism - a
 * transient `JSESSIONID` cookie appears only during the few seconds of the Google
 * handshake as an accepted side effect of using Spring's default wiring rather than a
 * custom cookie-based authorization-request repository.
 */
@Component
class GoogleOidcSuccessHandler(
    private val googleAuthService: GoogleAuthService,
    private val sessionService: SessionService,
    private val cookies: SessionCookieFactory,
    @Value($$"${app.frontend-url}") private val frontendUrl: String
) : AuthenticationSuccessHandler {

    private val log = LoggerFactory.getLogger(GoogleOidcSuccessHandler::class.java)

    override fun onAuthenticationSuccess(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authentication: Authentication
    ) {
        try {
            val oidcUser = authentication.principal as OidcUser

            val user = googleAuthService.resolveOrCreateUser(
                subject = oidcUser.idToken.subject,
                email = oidcUser.email,
                emailVerified = oidcUser.emailVerified ?: false,
                name = oidcUser.fullName ?: oidcUser.email ?: "Google user"
            )

            val sessionToken = sessionService.createSession(user.id.dbNotNull("id"))

            val redirectPath = sanitizeRedirectPath(
                request.cookies?.firstOrNull { it.name == "post_login_redirect" }?.value
            )

            val target = "$frontendUrl$redirectPath"
            val sessionCookieHeader = cookies.session(sessionToken).toString()
            log.info(
                "Google sign-in succeeded for {}, redirecting to {}. Response committed before headers? {}. Set-Cookie: {}",
                user.email, target, response.isCommitted, sessionCookieHeader
            )

            response.addHeader(HttpHeaders.SET_COOKIE, sessionCookieHeader)
            response.addHeader(HttpHeaders.SET_COOKIE, cookies.clearedRedirect().toString())
            log.info("Response committed after headers, before redirect? {}", response.isCommitted)
            response.sendRedirect(target)
        } catch (ex: Exception) {
            // Without this, an exception thrown anywhere above propagates straight out
            // of Spring Security's OAuth2 login filter with no application-level log at
            // all - the only trace of what actually failed would be whatever generic
            // error page/exception the servlet container happens to produce for it.
            log.error("Google sign-in's success handler failed", ex)
            throw ex
        }
    }
}
