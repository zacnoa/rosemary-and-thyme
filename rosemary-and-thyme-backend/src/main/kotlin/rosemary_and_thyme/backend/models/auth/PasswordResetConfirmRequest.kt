package rosemary_and_thyme.backend.models.auth

/**
 * Request body for `POST /auth/password-reset/confirm` - the raw token from the
 * emailed reset link, plus the new password to set.
 *
 * @property token the raw reset token from the emailed link; only its SHA-256 hash
 * is ever stored, so this raw value is checked against that hash (mirrors
 * [VerifyEmailRequest.token])
 * @property newPassword the plaintext password to hash and store
 */
data class PasswordResetConfirmRequest(val token: String, val newPassword: String)
