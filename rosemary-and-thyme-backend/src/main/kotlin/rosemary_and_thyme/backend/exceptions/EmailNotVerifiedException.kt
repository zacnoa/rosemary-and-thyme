package rosemary_and_thyme.backend.exceptions

/**
 * Thrown by [rosemary_and_thyme.backend.services.auth.AuthService.login] when the
 * account exists and the password matches, but `email_verified` is still `false`.
 * Mapped to 403 by [rosemary_and_thyme.backend.controllers.GlobalExceptionHandler.handleEmailNotVerified].
 */
class EmailNotVerifiedException(message: String) : RuntimeException(message)
