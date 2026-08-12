package rosemary_and_thyme.backend.models.auth

import java.util.UUID

/**
 * Public-facing view of an authenticated user, returned by `GET /user/aboutme`.
 * Deliberately excludes the password hash and other internal columns of
 * [rosemary_and_thyme.backend.jooqgenerated.app_auth.tables.records.UsersRecord].
 *
 * @property id the user's unique id
 * @property username the user's display name
 * @property email the user's verified email address
 */
data class UserDTO(val id: UUID, val username: String, val email: String)
