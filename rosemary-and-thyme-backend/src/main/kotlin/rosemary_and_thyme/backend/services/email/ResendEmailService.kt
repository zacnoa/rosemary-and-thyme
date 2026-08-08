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
