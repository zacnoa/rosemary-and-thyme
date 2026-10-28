package rosemary_and_thyme.models.user

import java.util.UUID

data class UserPrincipal(val id: UUID, val username: String, val email: String) {
    fun toDTO(): UserDTO =
        UserDTO(id = this.id.toString(), username = this.username, email = this.email)


}