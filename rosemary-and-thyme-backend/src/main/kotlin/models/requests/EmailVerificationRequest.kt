package rosemary_and_thyme.models.requests

import kotlinx.serialization.Serializable

/**
 * Requests another email-verification link.
 *
 * @property email email address associated with the unverified account
 */
@Serializable
data class EmailVerificationRequest(val email: String)

/**
 * Confirms an email address using the token received through the verification link.
 *
 * @property token raw single-use verification token, not its stored hash
 */
@Serializable
data class ConfirmEmailVerificationRequest(val token: String)
