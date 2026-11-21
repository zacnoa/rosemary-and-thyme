package rosemary_and_thyme.services.email_service

import com.resend.services.emails.model.CreateEmailOptions
import io.ktor.http.encodeURLParameter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rosemary_and_thyme.services.EmailService
import rosemary_and_thyme.services.Service
import rosemary_and_thyme.services.auth_service.PasswordResetDelivery

/**
 * Sends the frontend password-reset link after the token transaction has committed.
 * The blocking Resend request runs on the IO dispatcher.
 *
 * @param delivery destination email and raw password-reset token
 * @throws com.resend.core.exception.ResendException if Resend rejects or cannot send the request
 */
suspend fun EmailService.sendPasswordResetEmail(delivery: PasswordResetDelivery) {
    val link = "${Service.frontendUrl}/auth/reset-password?token=${delivery.token.encodeURLParameter()}"
    val email = CreateEmailOptions.builder()
        .from(Service.emailSender)
        .to(delivery.email)
        .subject("Reset your password")
        .text("Reset your password by opening this link:\n$link\n\nThis link expires in one hour.")
        .build()

    withContext(Dispatchers.IO) {
        Service.resendClient.emails().send(email)
    }
}
