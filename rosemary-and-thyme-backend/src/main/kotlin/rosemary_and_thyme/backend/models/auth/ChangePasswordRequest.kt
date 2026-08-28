package rosemary_and_thyme.backend.models.auth

/**
 * Request body for `POST /user/password` - changing your password while already
 * logged in (current + new password, no email round trip). The "forgot password"
 * counterpart is [PasswordResetConfirmRequest]/[PasswordResetRequestRequest], which
 * go through an emailed token instead since there's no session to authenticate with
 * at that point.
 *
 * @property currentPassword checked against the stored BCrypt hash in
 * [rosemary_and_thyme.backend.services.auth.AuthService.changePassword]
 * @property newPassword the plaintext password to hash and store
 */
data class ChangePasswordRequest(val currentPassword: String, val newPassword: String)
