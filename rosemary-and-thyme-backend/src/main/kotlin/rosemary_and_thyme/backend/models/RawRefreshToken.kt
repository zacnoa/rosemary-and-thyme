package rosemary_and_thyme.backend.models

import java.util.UUID

data class RawRefreshToken(val value:String, val familyId: UUID) {
}