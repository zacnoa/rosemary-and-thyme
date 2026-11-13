package rosemary_and_thyme.services.auth_service

import io.ktor.server.plugins.BadRequestException
import org.jooq.impl.DSL
import rosemary_and_thyme.jooq.app_auth.tables.references.PASSWORD_RESET_TOKENS
import rosemary_and_thyme.jooq.app_auth.tables.references.USERS
import rosemary_and_thyme.models.exceptions.ResourceNotFoundException
import rosemary_and_thyme.services.AuthService
import rosemary_and_thyme.services.CryptoService
import rosemary_and_thyme.services.Service
import java.time.OffsetDateTime

/**
 * Holds a reset token for the future email sender; it must never be returned by an HTTP route.
 * This is deliberately not a data class to avoid exposing the token through generated toString().
 *
 * @property email destination address stored on the account
 * @property token raw token to include in the reset link
 */
class PasswordResetDelivery(val email: String, val token: String)

/**
 * Issues a one-hour reset token, storing only its SHA-256 hash.
 *
 * @param email account email, taken from the principal or the public request body
 * @return password-reset delivery details
 * @throws BadRequestException if the email is blank or too long
 * @throws ResourceNotFoundException if the account does not exist
 * @throws IllegalStateException if required account data is missing or the token cannot be created
 */
fun AuthService.requestPasswordReset(email: String): PasswordResetDelivery {
    val normalizedEmail = email.trim().lowercase()
    if (normalizedEmail.isBlank() || normalizedEmail.length > 255) {
        throw BadRequestException("A valid email address is required")
    }

    return Service.dsl.transactionResult { configuration ->
        val tx = DSL.using(configuration)
        // All reset operations lock the user before modifying their tokens.
        val user = tx.selectFrom(USERS)
            .where(DSL.lower(USERS.EMAIL).eq(normalizedEmail))
            .forUpdate()
            .fetchOne()
            ?: throw ResourceNotFoundException("The account could not be found")

        val userId = user.id
        checkNotNull(userId) { "The account is missing an id" }
        val userEmail = user.email
        checkNotNull(userEmail) { "The account is missing an email address" }

        val token = CryptoService.generateRandomToken()
        check(tx.insertInto(PASSWORD_RESET_TOKENS)
            .set(PASSWORD_RESET_TOKENS.USER_ID, userId)
            .set(PASSWORD_RESET_TOKENS.TOKEN_HASH, CryptoService.sha256(token))
            .set(PASSWORD_RESET_TOKENS.EXPIRES_AT, OffsetDateTime.now().plusHours(1))
            .execute() == 1) { "The password reset token could not be created" }

        PasswordResetDelivery(userEmail, token)
    }
}
