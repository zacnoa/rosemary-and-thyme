package rosemary_and_thyme.backend.models.auth

/**
 * Request body for `POST /auth/login`.
 *
 * @property email the account's email address
 * @property password the account's plaintext password, checked against the stored
 * BCrypt hash in [rosemary_and_thyme.backend.services.auth.AuthService.login]
 */
data class LoginRequest(val email: String, val password: String)
