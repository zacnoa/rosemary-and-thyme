package rosemary_and_thyme.modules

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.install
import io.ktor.server.auth.oidc.Oidc
import io.ktor.server.response.respondRedirect
import io.ktor.server.sessions.sessions
import io.ktor.server.sessions.set
import io.ktor.utils.io.ExperimentalKtorApi
import rosemary_and_thyme.models.exceptions.AuthenticationException
import rosemary_and_thyme.services.Service
import rosemary_and_thyme.services.auth_service.createSession
import rosemary_and_thyme.services.auth_service.loginOrRegisterGoogleUser

/**
 * Configures Google OpenID Connect login with the authorization-code flow.
 * A verified Google identity is resolved to a local user, issued an application session,
 * and redirected to the configured frontend URL.
 */
@OptIn(ExperimentalKtorApi::class)
suspend fun Application.configureOIDC() {

    val oidc = install(Oidc)
    val env = environment.config.config("google")
    val frontendUrl=environment.config.config("frontend").property("url").getString()

    oidc.identityProvider("google") {
        issuer = "https://accounts.google.com"

        oauth {
            clientId = env.property("clientId").getString()
            clientSecret = env.property("clientSecret").getString()

            scopes = listOf("openid", "email", "profile")

            disableSessions()

            onAuthenticated { token ->
                val userInfo = token.userInfo

                val email = userInfo.email ?: throw AuthenticationException("Google did not provide an email")

                if (userInfo.emailVerified != true) {
                    throw AuthenticationException("Google email is not verified")

                }
                val user = Service.authService.loginOrRegisterGoogleUser(
                    subject = userInfo.subject,
                    email = email,
                )
                val session=Service.authService.createSession(user.id)
                call.sessions.set(session)
                
                call.respondRedirect(frontendUrl)

            }
        }
    }

}
