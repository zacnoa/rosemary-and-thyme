package rosemary_and_thyme.backend.models.auth

/** Request body for `POST /auth/login`. */
data class LoginRequest(val email: String, val password: String)
