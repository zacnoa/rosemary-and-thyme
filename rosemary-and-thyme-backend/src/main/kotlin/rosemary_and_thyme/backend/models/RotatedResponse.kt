package rosemary_and_thyme.backend.models

import java.util.UUID

data class RotatedResponse(val refreshToken:String, val userId: UUID)
