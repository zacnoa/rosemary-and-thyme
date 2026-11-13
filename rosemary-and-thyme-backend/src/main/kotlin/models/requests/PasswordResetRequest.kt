package rosemary_and_thyme.models.requests

import kotlinx.serialization.Serializable

/**
 * Requests a password-reset link for an account.
 *
 * @property email email address used to locate the account
 */
@Serializable
data class PasswordResetRequest(val email: String)

/**
 * Confirms a password reset using the raw token delivered by email.
 *
 * @property token single-use reset token, not its stored hash
 * @property newPassword plaintext password to encode before storage
 */
@Serializable
data class ConfirmPasswordResetRequest(val token: String, val newPassword: String)
