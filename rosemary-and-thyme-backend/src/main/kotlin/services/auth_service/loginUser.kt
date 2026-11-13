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
 * Authenticates a verified user and creates a session in the same transaction.
 * The user row remains locked during password verification and session creation to coordinate with password resets.
 *
 * @param email email address associated with the account
 * @param password plaintext password supplied by the user
 * @return the new [UserSession] after the transaction commits
 * @throws AuthenticationException if the credentials are invalid
 * @throws EmailNotVerified if the credentials are valid but the email is not verified
 */
fun AuthService.loginUser(email: String, password: String): UserSession {

    val normalizedEmail = email.trim().lowercase()

    return Service.dsl.transactionResult { configuration ->
        val tx = DSL.using(configuration)
        val user = tx.selectFrom(USERS)
            .where(USERS.EMAIL.eq(normalizedEmail))
            .forUpdate()
            .fetchOne()
            ?: throw AuthenticationException("The email or password is not correct")

        val passwordHash = user.password
            ?: throw AuthenticationException("The email or password is not correct")

        if (!CryptoService.matchesPassword(password, passwordHash)) {
            throw AuthenticationException("The email or password is not correct")
        }

        if (user.emailVerified != true) {
            throw EmailNotVerified("Please verify your email address before logging in")
        }

        val userId = user.id
        checkNotNull(userId) { "The account is missing an id" }
        Service.authService.createSession(userId, tx)
    }
}
