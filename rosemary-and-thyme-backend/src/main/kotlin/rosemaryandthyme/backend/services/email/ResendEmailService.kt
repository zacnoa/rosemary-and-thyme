package rosemaryandthyme.backend.services.email

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient

/** Sends transactional verification and password-reset emails through Resend. */
@Service
class ResendEmailService(
    @Value($$"${resend.api-key}") private val apiKey: String,
    @Value($$"${resend.from}") private val fromAddress: String
) {

    private val client = RestClient.builder()
        .baseUrl("https://api.resend.com")
        .defaultHeader("Authorization", "Bearer $apiKey")
        .build()

     /** Sends an account-verification email.
      * @param to the recipient email address.
      * @param verificationLink the verification URL.
      */
     fun sendVerificationEmail(to: String, verificationLink: String) {
        client.post()
            .uri("/emails")
            .body(
                mapOf(
                    "from" to fromAddress,
                    "to" to listOf(to),
                    "subject" to "Email verification - Rosemary & Thyme",
                    "html" to """
                        <p>Hello!</p>
                        <p>Click on this link to verify your email and finish registering your account.</p>
                        <p><a href="$verificationLink">$verificationLink</a></p>
                        <p>The link is valid for 24h, if you have not requested this email feel free to ignore this email.</p>
                    """.trimIndent()
                )
            )
            .retrieve()
            .toBodilessEntity()
    }

    /** Sends a password-reset email.
     * @param to the recipient email address.
     * @param resetLink the password-reset URL.
     */
    fun sendPasswordResetEmail(to: String, resetLink: String) {
        client.post()
            .uri("/emails")
            .body(
                mapOf(
                    "from" to fromAddress,
                    "to" to listOf(to),
                    "subject" to "Password reset - Rosemary & Thyme",
                    "html" to """
                        <p>Hi!</p>
                        <p>You requested a password reset. Click on the link below to change your password.</p>
                        <p><a href="$resetLink">$resetLink</a></p>
                        <p>The link is valid for one hour, if you have not requested a password reset feel free to ignore this email</p>
                    """.trimIndent()
                )
            )
            .retrieve()
            .toBodilessEntity()
    }
}
