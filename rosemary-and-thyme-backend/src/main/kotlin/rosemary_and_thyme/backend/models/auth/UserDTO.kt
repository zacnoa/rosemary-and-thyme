package rosemary_and_thyme.backend.models.auth

import java.time.OffsetDateTime
import java.util.UUID

/** Public-facing view of an authenticated user, returned by `GET /user/aboutme`. */
data class UserDTO(
    val id: UUID,
    val username: String,
    val email: String,
    val deletionRequestedAt: OffsetDateTime? = null,
    val hasPassword: Boolean
)
