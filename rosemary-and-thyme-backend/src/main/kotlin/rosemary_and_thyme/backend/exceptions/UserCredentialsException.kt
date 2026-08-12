package rosemary_and_thyme.backend.exceptions

/**
 * Thrown by [rosemary_and_thyme.backend.services.auth.AuthService.login] when the
 * email is unknown or the password doesn't match. Mapped to 401 by
 * [rosemary_and_thyme.backend.controllers.GlobalExceptionHandler.handleUserCredentials].
 *
 * @param message shown to the client as-is; deliberately generic ("email or password
 * not correct") so it can't be used to enumerate registered emails
 */
class UserCredentialsException(message: String) : RuntimeException(message)
