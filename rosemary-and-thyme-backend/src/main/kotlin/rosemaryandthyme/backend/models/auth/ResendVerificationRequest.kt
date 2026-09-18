package rosemaryandthyme.backend.models.auth

/** Request body for `POST /auth/verify-email/resend`. */
data class ResendVerificationRequest(val email: String)
