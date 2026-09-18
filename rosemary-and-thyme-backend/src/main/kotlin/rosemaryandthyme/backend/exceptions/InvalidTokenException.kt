package rosemaryandthyme.backend.exceptions

/** Thrown by [rosemaryandthyme.backend.services.auth.EmailVerificationService.verify] when a verification token's hash matches no row, or matches one… */
class InvalidTokenException(message: String) : RuntimeException(message)
