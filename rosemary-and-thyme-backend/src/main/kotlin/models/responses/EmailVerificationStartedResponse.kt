package rosemary_and_thyme.models.responses

import kotlinx.serialization.Serializable

/**
 * Result of starting email verification for a new account.
 *
 * @property verificationId identifier the client uses to confirm or resend the code
 * @property emailSent whether the email provider accepted the first delivery request
 */
@Serializable
data class EmailVerificationStartedResponse(val verificationId: String, val emailSent: Boolean)
