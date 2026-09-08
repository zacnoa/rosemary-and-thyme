package rosemary_and_thyme.backend.models.auth

/** Request body for `POST /auth/verify-email`. */
data class VerifyEmailRequest(val token: String)
