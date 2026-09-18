package rosemaryandthyme.backend.services.auth

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rosemaryandthyme.backend.exceptions.EmailNotVerifiedException
import rosemaryandthyme.backend.jooqgenerated.app_auth.tables.records.UsersRecord
import rosemaryandthyme.backend.repositories.auth.OauthIdentityRepository
import rosemaryandthyme.backend.repositories.auth.UserRepository
import rosemaryandthyme.backend.repositories.dbNotNull

private const val GOOGLE_PROVIDER = "google"

/** Resolves verified Google identities to local application accounts. */
@Service
class GoogleAuthService(
    private val userRepository: UserRepository,
    private val oauthIdentityRepository: OauthIdentityRepository
) {

    /** Finds, links, or creates the user for a verified Google identity.
     * @param subject Google's stable account identifier.
     * @param email the email claim supplied by Google.
     * @param emailVerified whether Google verified the email claim.
     * @param name the display name used for a new account.
     * @return the resolved local user.
     * @throws EmailNotVerifiedException if a new identity has no verified email.
     */
    @Transactional
    fun resolveOrCreateUser(subject: String, email: String?, emailVerified: Boolean, name: String): UsersRecord {

        oauthIdentityRepository.selectUserIdByIdentity(GOOGLE_PROVIDER, subject)?.let { userId ->
            return userRepository.selectUserById(userId)
                ?: throw IllegalStateException("oauth_identities row points at a user that no longer exists")
        }

        val verifiedEmail = email?.takeIf { emailVerified }
            ?: throw EmailNotVerifiedException("This Google account must have a verified email")

        val user = userRepository.selectUserByEmail(verifiedEmail) ?: userRepository.insertOauthUser(
            email = verifiedEmail,
            userName = name
        )

        oauthIdentityRepository.insertIdentity(user.id.dbNotNull("id"), GOOGLE_PROVIDER, subject)

        return user
    }
}
