package rosemaryandthyme.backend.models.auth

/** Request body for `POST /auth/register`. */
data class RegisterRequest(val username: String, val email: String, val password: String)
