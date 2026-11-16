package rosemary_and_thyme.models.requests

import kotlinx.serialization.Serializable

/**
 * Requests a replacement email verification code.
 *
 * @property verificationId identifier returned when the registration started
 */
@Serializable
data class ResendEmailVerificationRequest(val verificationId: String)

/**
 * Confirms an email address using the registration identifier and the code sent by email.
 *
 * @property verificationId identifier returned when the registration started
 * @property code six-digit code sent by email, including any leading zeros
 */
@Serializable
data class ConfirmEmailVerificationRequest(val verificationId: String, val code: String)
