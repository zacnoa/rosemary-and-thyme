package rosemary_and_thyme.modules

import com.resend.Resend
import io.ktor.server.application.Application

/**
 * Creates the Resend client using the API key from application configuration.
 *
 * @return client used to send account verification and password-reset emails
 */
fun Application.configureResendClient(): Resend {
    val resendConfig = environment.config.config("resend")
    return Resend(resendConfig.property("apiKey").getString())
}
