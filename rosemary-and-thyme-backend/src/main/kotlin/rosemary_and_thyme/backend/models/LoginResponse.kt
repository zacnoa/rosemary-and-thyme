package rosemary_and_thyme.backend.models

data class LoginResponse(
    val accessToken: String,
    val refreshToken: RawRefreshToken
)
