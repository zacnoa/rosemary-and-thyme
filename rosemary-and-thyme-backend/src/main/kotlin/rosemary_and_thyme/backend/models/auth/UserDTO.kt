package rosemary_and_thyme.backend.models.auth

import java.time.OffsetDateTime
import java.util.UUID

/**
 * Public-facing view of an authenticated user, returned by `GET /user/aboutme`.
 * Deliberately excludes the password hash and other internal columns of
 * [rosemary_and_thyme.backend.jooqgenerated.app_auth.tables.records.UsersRecord].
 *
 * @property id the user's unique id
 * @property username the user's display name
 * @property email the user's verified email address
 * @property deletionRequestedAt `null` unless the account is scheduled for deletion
 * (see `UserController.requestAccountDeletion`) - the frontend uses this to show the
 * "your account will be deleted in N days, cancel?" banner and compute N itself
 * (30 days out from this timestamp), rather than the backend precomputing a
 * days-remaining number that would go stale between page loads.
 * @property hasPassword whether this account has a password to check against at all
 * (`users.password IS NOT NULL`) - `false` only for a Google-only account that was
 * never also password-registered (see
 * [rosemary_and_thyme.backend.repositories.auth.UserRepository.insertOauthUser]). A
 * hybrid account (password-registered, later linked to Google too - see
 * [rosemary_and_thyme.backend.services.auth.GoogleAuthService]) still has one, so this
 * stays `true` for it. `components/dashboard/AccountSettings.tsx` uses this to hide
 * the change-password form (with an explanation) for accounts where it would just
 * fail with `GoogleAccountException` on submit.
 */
data class UserDTO(
    val id: UUID,
    val username: String,
    val email: String,
    val deletionRequestedAt: OffsetDateTime? = null,
    val hasPassword: Boolean
)
