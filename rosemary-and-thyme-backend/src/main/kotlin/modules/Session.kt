package rosemary_and_thyme.modules

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.install
import io.ktor.server.sessions.Sessions
import io.ktor.server.sessions.cookie
import io.ktor.server.sessions.maxAge
import kotlin.time.Duration.Companion.days
import rosemary_and_thyme.models.auth.UserSession

/**
 * Configures cookie-based storage for [UserSession]. The cookie is HTTP-only, HTTPS-only, and
 * expires after 30 days.
 */
fun Application.configureSession() {
  install(Sessions) {
    cookie<UserSession>("user_session") {
      cookie.path = "/"
      cookie.maxAge = 30.days
      cookie.httpOnly = true
      cookie.secure = true
    }
  }
}
