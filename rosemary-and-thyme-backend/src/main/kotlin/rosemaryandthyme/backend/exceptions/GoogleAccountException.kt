package rosemaryandthyme.backend.exceptions

/** Thrown by [rosemaryandthyme.backend.services.auth.AuthService.login] and [rosemaryandthyme.backend.services.auth.AuthService.changePassword] when the… */
class GoogleAccountException(message: String) : RuntimeException(message)
