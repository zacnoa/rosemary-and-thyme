package rosemary_and_thyme.backend.exceptions

/** Thrown by [rosemary_and_thyme.backend.services.auth.EmailVerificationService.verify] when a verification token's hash matches no row, or matches one… */
class InvalidTokenException(message: String) : RuntimeException(message)
