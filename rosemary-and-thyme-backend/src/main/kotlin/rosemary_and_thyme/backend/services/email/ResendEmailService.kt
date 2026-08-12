package rosemary_and_thyme.backend.services.email

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient

/**
 * Sends transactional emails through the Resend API (https://resend.com/docs/api-reference/emails/send-email).
 * Plain RestClient call - no SDK dependency needed for a single endpoint.
 */
@Service
class ResendEmailService(
    @Value($$"${resend.api-key}") private val apiKey: String,
    @Value($$"${resend.from}") private val fromAddress: String
) {

    private val client = RestClient.builder()
        .baseUrl("https://api.resend.com")
        .defaultHeader("Authorization", "Bearer $apiKey")
        .build()

     /**
      * Sends the account-verification email. The only caller is
      * [rosemary_and_thyme.backend.services.auth.EmailVerificationService], which builds
      * [verificationLink] and owns the workflow this email is a step of - this class only
      * knows how to render and deliver an email, not when to send one.
      *
      * Email structure: a `POST /emails` call to Resend's send-email endpoint with a
      * fixed `from` (this deployment's sender address), a single `to` recipient, a
      * hardcoded (Croatian) `subject`, and an inline `html` body built with Kotlin's
      * `trimIndent()` multi-line string - no template engine or external template file,
      * since this is currently the app's only transactional email. The body is a short
      * greeting, an anchor tag around [verificationLink] (both as link text and href, so
      * it's readable even if the email client doesn't render the anchor as a button), and
      * a note that the link expires in 24 hours (matching the token TTL enforced in
      * [rosemary_and_thyme.backend.repositories.auth.EmailVerificationRepository.selectValidToken]).
      *
      * Fire-and-forget: the response body is discarded ([toBodilessEntity]) and Resend
      * API errors propagate as an unchecked exception from `retrieve()`, which surfaces
      * to the client as a generic 500 via [rosemary_and_thyme.backend.controllers.GlobalExceptionHandler.handleGeneric]
      * rather than a specific "email failed to send" error.
      *
      * @param to the recipient's email address
      * @param verificationLink the full, clickable verification URL (frontend origin +
      * `/auth/verify-email` + the raw token as a query param)
      */
     fun sendVerificationEmail(to: String, verificationLink: String) {
        client.post()
            .uri("/emails")
            .body(
                mapOf(
                    "from" to fromAddress,
                    "to" to listOf(to),
                    "subject" to "Potvrdi svoj email - Rosemary & Thyme",
                    "html" to """
                        <p>Bok!</p>
                        <p>Klikni na link ispod da potvrdiš svoj email i dovršiš registraciju:</p>
                        <p><a href="$verificationLink">$verificationLink</a></p>
                        <p>Link vrijedi 24 sata. Ako nisi ti tražio ovaj email, slobodno ga ignoriraj.</p>
                    """.trimIndent()
                )
            )
            .retrieve()
            .toBodilessEntity()
    }
}
