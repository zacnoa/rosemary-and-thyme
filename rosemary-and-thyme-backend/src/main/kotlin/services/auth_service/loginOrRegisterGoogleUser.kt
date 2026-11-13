package rosemary_and_thyme.services.auth_service

import org.jooq.impl.DSL
import rosemary_and_thyme.jooq.app_auth.tables.references.OAUTH_IDENTITIES
import rosemary_and_thyme.jooq.app_auth.tables.references.USERS
import rosemary_and_thyme.models.exceptions.AuthenticationException
import rosemary_and_thyme.models.user.UserPrincipal
import rosemary_and_thyme.models.user.toUserPrincipal
import rosemary_and_thyme.services.AuthService
import rosemary_and_thyme.services.Service
import rosemary_and_thyme.services.UsernameService

/**
 * Resolves a verified Google identity to a local user in a single database transaction.
 * Existing verified accounts are linked by normalized email; otherwise a new account and
 * OAuth identity are created with an automatically generated username.
 * Concurrent inserts are resolved by re-reading the account and the persisted OAuth identity.
 * New OAuth links are inserted only when the selected account has a verified email.
 *
 * @param subject stable Google OpenID Connect subject identifier
 * @param email Google-verified email address used to find or create the local account
 * @return principal for the resolved local user
 * @throws AuthenticationException if the email is invalid or no OAuth link to a verified account can be resolved
 * @throws IllegalStateException if a unique username cannot be persisted or required account data is missing
 */
fun AuthService.loginOrRegisterGoogleUser(subject: String, email: String): UserPrincipal {

    return Service.dsl.transactionResult { configuration ->

        val tx = DSL.using(configuration)
        val normalizedEmail = email.trim().lowercase()

        if (normalizedEmail.isBlank()) {
            throw AuthenticationException("Google did not provide a valid email")
        }

        val identityUser = tx.select(USERS.ID, USERS.EMAIL, USERS.USERNAME)
            .from(OAUTH_IDENTITIES)
            .join(USERS)
            .on(USERS.ID.eq(OAUTH_IDENTITIES.USER_ID))
            .where(OAUTH_IDENTITIES.PROVIDER.eq("google"))
            .and(OAUTH_IDENTITIES.PROVIDER_SUBJECT.eq(subject))
            .fetchOneInto(USERS)

        if (identityUser != null) {
            return@transactionResult identityUser.toUserPrincipal()
        }

        val existingUser = tx.selectFrom(USERS)
            .where(DSL.lower(USERS.EMAIL).eq(normalizedEmail))
            .fetchOne()

        val userId = if (existingUser != null) {
            checkNotNull(existingUser.id) { "Existing user record is missing an id" }
        } else {
            checkNotNull(

                (1..2).firstNotNullOfOrNull {
                    val username = UsernameService.generateUsername()

                    val insertedUserId = tx.insertInto(USERS)
                        .set(USERS.USERNAME, username)
                        .set(USERS.USERNAME_NORMALIZED, username)
                        .set(USERS.EMAIL, normalizedEmail)
                        .set(USERS.EMAIL_VERIFIED, true)
                        .onConflict()
                        .doNothing()
                        .returning(USERS.ID)
                        .fetchOne()
                        ?.get(USERS.ID)

                    // The insert can conflict either because the generated username already exists
                    // or because another request concurrently created the user for this email.
                    // Reuse the concurrently created user; otherwise retry with a new username.
                    insertedUserId ?: tx.select(USERS.ID)
                        .from(USERS)
                        .where(DSL.lower(USERS.EMAIL).eq(normalizedEmail))
                        .fetchOne()
                        ?.value1()
                }
            ){"We could not generate a unique username"}

        }

        tx.insertInto(OAUTH_IDENTITIES)
            .columns(
                OAUTH_IDENTITIES.PROVIDER,
                OAUTH_IDENTITIES.PROVIDER_SUBJECT,
                OAUTH_IDENTITIES.USER_ID
            )
            .select(
                tx.select(
                    DSL.`val`("google"),
                    DSL.`val`(subject),
                    USERS.ID
                )
                    .from(USERS)
                    .where(USERS.ID.eq(userId))
                    .and(USERS.EMAIL_VERIFIED.eq(true))
            )
            .onConflict(OAUTH_IDENTITIES.PROVIDER, OAUTH_IDENTITIES.PROVIDER_SUBJECT)
            .doNothing()
            .execute()

        val resolvedUser = tx.select(USERS.ID, USERS.EMAIL, USERS.USERNAME)
            .from(OAUTH_IDENTITIES)
            .join(USERS)
            .on(USERS.ID.eq(OAUTH_IDENTITIES.USER_ID))
            .where(OAUTH_IDENTITIES.PROVIDER.eq("google"))
            .and(OAUTH_IDENTITIES.PROVIDER_SUBJECT.eq(subject))
            .fetchOneInto(USERS)
            ?: throw AuthenticationException(
                "The Google identity could not be linked to a verified account"
            )
        resolvedUser.toUserPrincipal()

    }
}
