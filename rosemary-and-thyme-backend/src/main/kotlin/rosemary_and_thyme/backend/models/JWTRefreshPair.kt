package rosemary_and_thyme.backend.models

import java.util.UUID

data class JWTRefreshPair(val refreshToken:String, val accessToken: String) {}