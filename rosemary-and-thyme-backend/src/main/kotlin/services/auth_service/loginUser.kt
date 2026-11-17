package rosemary_and_thyme.services.auth_service

import org.jooq.impl.DSL
import rosemary_and_thyme.jooq.app_auth.tables.references.USERS
import rosemary_and_thyme.models.exceptions.AuthenticationException
import rosemary_and_thyme.models.exceptions.EmailNotVerified
import rosemary_and_thyme.models.auth.UserSession
import rosemary_and_thyme.services.AuthService
import rosemary_and_thyme.services.CryptoService
import rosemary_and_thyme.services.Service

/**
 * Checks the password before opening the session transaction.
 * The transaction locks the user and rechecks the stored hash to coordinate with password resets.
 *
 * @param email email address associated with the account
 * @param password plaintext password supplied by the user
 * @return the new [UserSession] after the transaction commits
 * @throws AuthenticationException if the credentials are invalid
 * @throws EmailNotVerified if the credentials are valid but the email is not verified
 */
fun AuthService.loginUser(email: String, password: String): UserSession {
    val normalizedEmail = email.trim().lowercase()
    val invalidCredentials = "The email or password is not correct"

    val candidate = Service.dsl.select(USERS.ID, USERS.PASSWORD)
        .from(USERS)
        .where(USERS.EMAIL.eq(normalizedEmail))
        .fetchOne() ?: throw AuthenticationException(invalidCredentials)

    val userId = checkNotNull(candidate.get(USERS.ID)) { "The account is missing an id" }
    val originalHash = candidate.get(USERS.PASSWORD)
        ?: throw AuthenticationException(invalidCredentials)

    if (!CryptoService.matchesPassword(password, originalHash)) {
        throw AuthenticationException(invalidCredentials)
    }

    return Service.dsl.transactionResult { configuration ->
        val tx = DSL.using(configuration)
        val current = tx.select(USERS.PASSWORD, USERS.EMAIL_VERIFIED)
            .from(USERS)
            .where(USERS.ID.eq(userId))
            .forUpdate()
            .fetchOne() ?: throw AuthenticationException(invalidCredentials)

        if (current.get(USERS.PASSWORD) != originalHash) {
            throw AuthenticationException(invalidCredentials)
        }

        if (current.get(USERS.EMAIL_VERIFIED) != true) {
            throw EmailNotVerified("Please verify your email address before logging in")
        }

        createSession(userId, tx)
    }
}
