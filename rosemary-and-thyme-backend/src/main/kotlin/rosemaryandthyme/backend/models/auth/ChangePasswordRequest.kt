package rosemaryandthyme.backend.models.auth

/** Request body for `POST /user/password` - changing your password while already logged in (current + new password, no email round trip). */
data class ChangePasswordRequest(val currentPassword: String, val newPassword: String)
