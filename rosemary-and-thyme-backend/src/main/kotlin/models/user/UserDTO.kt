package rosemary_and_thyme.models.user

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Public account information returned for an authenticated user.
 *
 * @property id user identifier serialized as a UUID string
 * @property username username used for display
 * @property email email address associated with the account
 */
@Serializable
data class UserDTO(val id: String, val username: String, val email: String)
