package rosemary_and_thyme.backend.models.auth

/** Request body for `POST /auth/register`. */
data class RegisterRequest(val username: String, val email: String, val password: String)
