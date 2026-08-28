package rosemary_and_thyme.backend.models.auth

/**
 * Request body for `POST /auth/password-reset/request` - kicks off the "forgot
 * password" flow by emailing a reset link, mirroring [ResendVerificationRequest]'s
 * shape exactly.
 *
 * @property email the account's email to send a reset link to
 */
data class PasswordResetRequestRequest(val email: String)
