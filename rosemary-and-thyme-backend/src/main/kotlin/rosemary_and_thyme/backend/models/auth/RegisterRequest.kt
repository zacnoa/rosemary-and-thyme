package rosemary_and_thyme.backend.models.auth

/**
 * Request body for `POST /auth/register`.
 *
 * @property username the display name to store for the new account
 * @property email the address that will receive the verification link; must be
 * unique - [rosemary_and_thyme.backend.repositories.auth.UserRepository.insertUser]
 * throws [rosemary_and_thyme.backend.exceptions.UserAlreadyExistsException] on conflict
 * @property password plaintext password; hashed with BCrypt before it is stored
 */
data class RegisterRequest(val username: String, val email: String, val password: String)
