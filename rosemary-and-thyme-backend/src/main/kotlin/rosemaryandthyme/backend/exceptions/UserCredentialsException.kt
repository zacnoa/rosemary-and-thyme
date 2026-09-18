package rosemaryandthyme.backend.exceptions

/** Thrown by [rosemaryandthyme.backend.services.auth.AuthService.login] when the email is unknown or the password doesn't match. */
class UserCredentialsException(message: String) : RuntimeException(message)
