package rosemary_and_thyme.services.email_service

import com.resend.services.emails.model.CreateEmailOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rosemary_and_thyme.services.EmailService
import rosemary_and_thyme.services.Service
import rosemary_and_thyme.services.auth_service.EmailVerificationDelivery

/**
 * Sends the email verification code after the verification transaction has committed.
 * The blocking Resend request runs on the IO dispatcher.
 *
 * @param delivery destination email and raw verification code
 * @throws com.resend.core.exception.ResendException if Resend rejects or cannot send the request
 */
suspend fun EmailService.sendVerificationEmail(delivery: EmailVerificationDelivery) {
    val email = CreateEmailOptions.builder()
        .from(Service.emailSender)
        .to(delivery.email)
        .subject("Confirm your email address")
        .text(
            """
            Your Rosemary & Thyme verification code is ${delivery.code}.

            This code expires in 5 minutes.

            If you didn't request this code, ignore this email or contact us at privacy@rosemary-n-thyme.com.
            """.trimIndent()
        )
        .build()

    withContext(Dispatchers.IO) {
        Service.resendClient.emails().send(email)
    }
}
