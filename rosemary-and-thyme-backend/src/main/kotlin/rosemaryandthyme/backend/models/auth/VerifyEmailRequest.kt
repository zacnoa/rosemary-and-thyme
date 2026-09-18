package rosemaryandthyme.backend.models.auth

/** Request body for `POST /auth/verify-email`. */
data class VerifyEmailRequest(val token: String)
