package rosemary_and_thyme.backend.exceptions

/**
 * Thrown by [rosemary_and_thyme.backend.services.auth.AuthService.login] and
 * [rosemary_and_thyme.backend.services.auth.AuthService.changePassword] when the
 * account has no password to check against (`users.password` is `null` - a
 * Google-only account, see
 * [rosemary_and_thyme.backend.repositories.auth.UserRepository.insertOauthUser]).
 *
 * Deliberately its own exception rather than reusing [UserCredentialsException]:
 * the frontend needs to tell "wrong password" apart from "this account has no
 * password at all" so `routes/auth/login.tsx` can redirect straight into the
 * Google sign-in flow instead of showing a generic credentials error. Mapped to 422
 * by [rosemary_and_thyme.backend.controllers.GlobalExceptionHandler.handleGoogleAccount].
 */
class GoogleAccountException(message: String) : RuntimeException(message)
