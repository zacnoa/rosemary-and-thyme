package rosemary_and_thyme.models.user

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class UserDTO(val id: String, val username: String, val email: String)
