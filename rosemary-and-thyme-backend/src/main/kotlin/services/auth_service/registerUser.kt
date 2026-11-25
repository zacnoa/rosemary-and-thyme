package rosemary_and_thyme.services.auth_service

import io.ktor.server.plugins.BadRequestException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jooq.impl.DSL
import rosemary_and_thyme.jooq.app_auth.tables.references.USERS
import rosemary_and_thyme.models.exceptions.UserAlreadyExists
import rosemary_and_thyme.services.AuthService
import rosemary_and_thyme.services.CryptoService
import rosemary_and_thyme.services.Service


/**
 * Registers a user while preserving the supplied username for display.
 * Username and email values are normalized separately for case-insensitive uniqueness checks.
 * The account and its first verification code are created in the same transaction.
 *
 * @param username display name selected by the user
 * @param password plaintext password to encode before storage
 * @param email email address associated with the account
 * @return verification ID for the client and code for email delivery
 * @throws BadRequestException if the normalized email is blank or too long
 * @throws UserAlreadyExists if the insert conflicts with an existing user
 */
suspend fun AuthService.registerUser(username: String, password: String, email: String): EmailVerificationDelivery {
    val normalizedUsername = username.trim().lowercase()
    val normalizedEmail = email.trim().lowercase()

    if (normalizedEmail.isBlank()) {
        throw BadRequestException("Email must not be blank")
    }

    val hashedPassword = CryptoService.hashPassword(password)

    return withContext(Dispatchers.IO) {
        Service.dsl.transactionResult { configuration ->
            val tx = DSL.using(configuration)
            tx.insertInto(USERS)
                .set(USERS.USERNAME, username)
                .set(USERS.USERNAME_NORMALIZED, normalizedUsername)
                .set(USERS.EMAIL, normalizedEmail)
                .set(USERS.PASSWORD, hashedPassword)
                .onConflict()
                .doNothing()
                .execute().also { affectedRows ->
                    if (affectedRows == 0) throw UserAlreadyExists("A user with that username already exists")
                }

            Service.authService.requestEmailVerification(normalizedEmail, tx)
        }
    }
}
