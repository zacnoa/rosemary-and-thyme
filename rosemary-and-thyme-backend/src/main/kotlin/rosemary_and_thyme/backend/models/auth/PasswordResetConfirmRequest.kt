package rosemary_and_thyme.backend.models.auth

/** Request body for `POST /auth/password-reset/confirm` - the raw token from the emailed reset link, plus the new password to set. */
data class PasswordResetConfirmRequest(val token: String, val newPassword: String)
