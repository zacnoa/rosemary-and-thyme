package rosemary_and_thyme.backend.exceptions

/**
 * Thrown by [rosemary_and_thyme.backend.services.auth.EmailVerificationService.verify]
 * when a verification token's hash matches no row, or matches one that's already used
 * or expired. Mapped to 400 by
 * [rosemary_and_thyme.backend.controllers.GlobalExceptionHandler.handleInvalidToken].
 */
class InvalidTokenException(message: String) : RuntimeException(message)
