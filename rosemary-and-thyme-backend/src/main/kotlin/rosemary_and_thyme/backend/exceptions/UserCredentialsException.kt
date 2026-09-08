package rosemary_and_thyme.backend.exceptions

/** Thrown by [rosemary_and_thyme.backend.services.auth.AuthService.login] when the email is unknown or the password doesn't match. */
class UserCredentialsException(message: String) : RuntimeException(message)
