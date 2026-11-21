package rosemary_and_thyme.models.user

import rosemary_and_thyme.jooq.app_auth.tables.records.UsersRecord
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

/**
 * Converts a database user record into an authenticated application principal.
 * Required user fields are checked at the persistence boundary so callers receive a complete principal.
 *
 * @return authenticated principal represented by this record
 * @throws IllegalStateException if a required user field is missing
 */
fun UsersRecord.toUserPrincipal(): UserPrincipal =
    UserPrincipal(
        id = checkNotNull(id) { "User record is missing an id" },
        username = checkNotNull(username) { "User record is missing a username" },
        email = checkNotNull(email) { "User record is missing an email" }
    )
