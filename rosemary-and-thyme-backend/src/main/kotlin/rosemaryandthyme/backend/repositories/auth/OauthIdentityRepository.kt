package rosemaryandthyme.backend.repositories.auth

import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import rosemaryandthyme.backend.jooqgenerated.app_auth.tables.references.OAUTH_IDENTITIES
import java.util.UUID

/** Provides database operations for OAuth identities linked to local users. */
@Repository
class OauthIdentityRepository(val dsl: DSLContext) {

    /** Finds the user linked to an OAuth identity.
     * @param provider the OAuth provider name.
     * @param subject the provider's stable account identifier.
     * @return the linked user id, or `null` when the identity is not linked.
     */
    fun selectUserIdByIdentity(provider: String, subject: String): UUID? =
        dsl.select(OAUTH_IDENTITIES.USER_ID)
            .from(OAUTH_IDENTITIES)
            .where(OAUTH_IDENTITIES.PROVIDER.eq(provider))
            .and(OAUTH_IDENTITIES.PROVIDER_SUBJECT.eq(subject))
            .fetchOne(OAUTH_IDENTITIES.USER_ID)

    /** Links an OAuth identity to a local user.
     * @param userId the local account to link.
     * @param provider the OAuth provider name.
     * @param subject the provider's stable account identifier.
     */
    fun insertIdentity(userId: UUID, provider: String, subject: String) {
        dsl.insertInto(OAUTH_IDENTITIES)
            .set(OAUTH_IDENTITIES.USER_ID, userId)
            .set(OAUTH_IDENTITIES.PROVIDER, provider)
            .set(OAUTH_IDENTITIES.PROVIDER_SUBJECT, subject)
            .execute()
    }
}
