package rosemary_and_thyme.backend.exceptions

/** Thrown by [rosemary_and_thyme.backend.services.auth.AuthService.login] and [rosemary_and_thyme.backend.services.auth.AuthService.changePassword] when the… */
class GoogleAccountException(message: String) : RuntimeException(message)
