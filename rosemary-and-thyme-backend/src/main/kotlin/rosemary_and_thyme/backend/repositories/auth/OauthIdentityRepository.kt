package rosemary_and_thyme.backend.repositories.auth

import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import rosemary_and_thyme.backend.jooqgenerated.app_auth.tables.references.OAUTH_IDENTITIES
import java.util.UUID

/**
 * `app_auth.oauth_identities` - the mapping between a third-party provider's own
 * user id and one of this app's own accounts. See
 * [rosemary_and_thyme.backend.services.auth.GoogleAuthService], the only caller,
 * for how this fits into the sign-in flow.
 */
@Repository
class OauthIdentityRepository(val dsl: DSLContext) {

    /** @return the linked account's id, or `null` if no account has this [provider]/[subject] linked yet */
    fun selectUserIdByIdentity(provider: String, subject: String): UUID? =
        dsl.select(OAUTH_IDENTITIES.USER_ID)
            .from(OAUTH_IDENTITIES)
            .where(OAUTH_IDENTITIES.PROVIDER.eq(provider))
            .and(OAUTH_IDENTITIES.PROVIDER_SUBJECT.eq(subject))
            .fetchOne(OAUTH_IDENTITIES.USER_ID)

    /**
     * Links [provider]/[subject] onto [userId]. Relies on the DB's
     * `oauth_identities_provider_subject_key` unique constraint to prevent the same
     * identity being linked twice - same pattern as [UserRepository.insertUser]
     * relying on `users_email_key`, though this method doesn't itself translate a
     * violation into a friendlier exception, since [GoogleAuthService][rosemary_and_thyme.backend.services.auth.GoogleAuthService]
     * already checks [selectUserIdByIdentity] first and only calls this for an
     * identity it just confirmed doesn't exist yet.
     */
    fun insertIdentity(userId: UUID, provider: String, subject: String) {
        dsl.insertInto(OAUTH_IDENTITIES)
            .set(OAUTH_IDENTITIES.USER_ID, userId)
            .set(OAUTH_IDENTITIES.PROVIDER, provider)
            .set(OAUTH_IDENTITIES.PROVIDER_SUBJECT, subject)
            .execute()
    }
}
