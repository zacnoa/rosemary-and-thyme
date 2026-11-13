package rosemary_and_thyme.services.email_service

import com.resend.services.emails.model.CreateEmailOptions
import io.ktor.http.encodeURLParameter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rosemary_and_thyme.services.EmailService
import rosemary_and_thyme.services.Service
import rosemary_and_thyme.services.auth_service.EmailVerificationDelivery

/**
 * Sends the frontend email-verification link after the token transaction has committed.
 * The blocking Resend request runs on the IO dispatcher.
 *
 * @param delivery destination email and raw verification token
 * @throws com.resend.core.exception.ResendException if Resend rejects or cannot send the request
 */
suspend fun EmailService.sendVerificationEmail(delivery: EmailVerificationDelivery) {
    val link = "${Service.frontendUrl}/auth/verify-email?token=${delivery.token.encodeURLParameter()}"
    val email = CreateEmailOptions.builder()
        .from(Service.emailSender)
        .to(delivery.email)
        .subject("Confirm your email address")
        .text("Confirm your email address by opening this link:\n$link\n\nThis link expires in 24 hours.")
        .build()

    withContext(Dispatchers.IO) {
        Service.resendClient.emails().send(email)
    }
}
