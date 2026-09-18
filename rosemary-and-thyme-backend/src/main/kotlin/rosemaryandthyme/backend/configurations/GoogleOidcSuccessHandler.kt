package rosemaryandthyme.backend.configurations

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.security.web.authentication.AuthenticationSuccessHandler
import org.springframework.stereotype.Component
import org.springframework.web.util.UriComponentsBuilder
import rosemaryandthyme.backend.exceptions.EmailNotVerifiedException
import rosemaryandthyme.backend.repositories.dbNotNull
import rosemaryandthyme.backend.services.auth.GoogleAuthService
import rosemaryandthyme.backend.services.auth.SessionCookieFactory
import rosemaryandthyme.backend.services.auth.SessionService
import rosemaryandthyme.backend.services.auth.sanitizeRedirectPath

/** Creates an application session after a successful Google OIDC login. */
@Component
class GoogleOidcSuccessHandler(
    private val googleAuthService: GoogleAuthService,
    private val sessionService: SessionService,
    private val cookies: SessionCookieFactory,
    @Value($$"${app.frontend-url}") private val frontendUrl: String
) : AuthenticationSuccessHandler {

    /** Resolves the Google identity, sets cookies, and redirects to the frontend.
     * @param request the OAuth callback request.
     * @param response the redirect response.
     * @param authentication the validated OIDC authentication.
     * @throws Exception if identity resolution or session creation fails.
     */
    override fun onAuthenticationSuccess(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authentication: Authentication
    ) {
        val redirectPath = sanitizeRedirectPath(
            request.cookies?.firstOrNull { it.name == "post_login_redirect" }?.value
        )

        try {
            val oidcUser = authentication.principal as OidcUser

            val user = googleAuthService.resolveOrCreateUser(
                subject = oidcUser.idToken.subject,
                email = oidcUser.email,
                emailVerified = oidcUser.emailVerified ?: false,
                name = oidcUser.fullName ?: oidcUser.email ?: "Google user"
            )

            val sessionToken = sessionService.createSession(user.id.dbNotNull("id"))

            val target = "$frontendUrl$redirectPath"
            val sessionCookieHeader = cookies.session(sessionToken).toString()

            response.addHeader(HttpHeaders.SET_COOKIE, sessionCookieHeader)
            response.addHeader(HttpHeaders.SET_COOKIE, cookies.clearedRedirect().toString())
            response.sendRedirect(target)
        } catch (_: EmailNotVerifiedException) {
            val target = UriComponentsBuilder.fromUriString(frontendUrl)
                .path("/auth/login")
                .queryParam("redirect", redirectPath)
                .queryParam("googleError", "unverified-email")
                .build()
                .encode()
                .toUriString()

            response.addHeader(HttpHeaders.SET_COOKIE, cookies.clearedRedirect().toString())
            response.sendRedirect(target)
        } catch (ex: Exception) {
            // Without this, an exception thrown anywhere above propagates straight out
            // of Spring Security's OAuth2 login filter with no application-level log at
            // all - the only trace of what actually failed would be whatever generic
            // error page/exception the servlet container happens to produce for it.
            throw ex
        }
    }
}
