package rosemary_and_thyme.models.user

import java.util.UUID

/**
 * Authenticated user identity attached to a protected Ktor call.
 *
 * @property id internal UUID of the authenticated user
 * @property username username used for display
 * @property email email address associated with the account
 */
data class UserPrincipal(val id: UUID, val username: String, val email: String) {
    /**
     * Converts this principal to its serializable response model.
     *
     * @return a [UserDTO] containing the principal's public account data
     */
    fun toDTO(): UserDTO =
        UserDTO(id = this.id.toString(), username = this.username, email = this.email)


}
