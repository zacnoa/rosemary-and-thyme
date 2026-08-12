package rosemary_and_thyme.backend.models.auth

/**
 * Request body for `POST /auth/verify-email/resend`.
 *
 * @property email the address to (re)send a verification link to. The endpoint always
 * responds 200 regardless of whether this email is registered or already verified -
 * see [rosemary_and_thyme.backend.services.auth.EmailVerificationService.resend].
 */
data class ResendVerificationRequest(val email: String)
