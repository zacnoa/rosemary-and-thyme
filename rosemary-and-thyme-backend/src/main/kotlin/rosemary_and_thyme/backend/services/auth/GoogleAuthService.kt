package rosemary_and_thyme.backend.services.auth

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rosemary_and_thyme.backend.jooqgenerated.app_auth.tables.records.UsersRecord
import rosemary_and_thyme.backend.repositories.auth.OauthIdentityRepository
import rosemary_and_thyme.backend.repositories.auth.UserRepository
import rosemary_and_thyme.backend.repositories.dbNotNull

private const val GOOGLE_PROVIDER = "google"

/**
 * Resolves a verified Google identity to one of this app's own accounts - the
 * decision [rosemary_and_thyme.backend.configurations.GoogleOidcSuccessHandler] hands
 * off to once Spring Security has already validated the ID token itself (signature,
 * issuer, audience, expiry - none of that is re-done here). Three cases, checked in
 * order:
 * 1. This Google subject is already linked (`app_auth.oauth_identities`) to an
 *    account - use it.
 * 2. It isn't, but Google reports the email as verified and a password account
 *    already exists with that email - auto-link this Google identity onto it. A
 *    deliberate choice: Google's own verification is trusted here rather than
 *    requiring some separate "confirm it's you" step before linking.
 * 3. Neither - create a brand-new account, already verified (Google did that part)
 *    and with no password (`app_auth.users.password` is nullable specifically for
 *    this case - see [UserRepository.insertOauthUser]).
 */
@Service
class GoogleAuthService(
    private val userRepository: UserRepository,
    private val oauthIdentityRepository: OauthIdentityRepository
) {

    /**
     * @param subject the ID token's `sub` claim - Google's own stable, never-reused
     * id for this account (never the email, which a Google account can change)
     * @param email the ID token's `email` claim, or `null` if Google didn't provide one
     * @param emailVerified the ID token's `email_verified` claim
     * @param name a display name for a brand-new account (case 3 above) - unused
     *  otherwise
     * @return the resolved or newly-created account
     * @throws IllegalArgumentException if [email] is `null` and no existing identity
     * matched - there's nothing to look up or create an account with otherwise
     */
    @Transactional
    fun resolveOrCreateUser(subject: String, email: String?, emailVerified: Boolean, name: String): UsersRecord {

        oauthIdentityRepository.selectUserIdByIdentity(GOOGLE_PROVIDER, subject)?.let { userId ->
            return userRepository.selectUserById(userId)
                ?: throw IllegalStateException("oauth_identities row points at a user that no longer exists")
        }

        val existingByEmail = if (emailVerified) email?.let(userRepository::selectUserByEmail) else null

        val user = existingByEmail ?: userRepository.insertOauthUser(
            email = requireNotNull(email) { "Google sign-in did not provide an email for this account" },
            userName = name
        )

        oauthIdentityRepository.insertIdentity(user.id.dbNotNull("id"), GOOGLE_PROVIDER, subject)

        return user
    }
}
