package rosemary_and_thyme.services.auth_service

import io.ktor.server.plugins.BadRequestException
import org.jooq.DSLContext
import org.jooq.impl.DSL
import rosemary_and_thyme.jooq.app_auth.tables.references.EMAIL_VERIFICATION_TOKENS
import rosemary_and_thyme.jooq.app_auth.tables.references.USERS
import rosemary_and_thyme.models.exceptions.ResourceNotFoundException
import rosemary_and_thyme.services.AuthService
import rosemary_and_thyme.services.CryptoService
import rosemary_and_thyme.services.Service
import java.time.OffsetDateTime

/**
 * Holds delivery details for the future email sender; HTTP responses must not expose the token.
 *
 * @property email destination email address stored on the account
 * @property token raw token to include in the verification link
 */
class EmailVerificationDelivery(val email: String, val token: String)

/**
 * Issues a 24-hour verification token for an unverified account, storing only its SHA-256 hash.
 * The user row is locked to coordinate issuance with email confirmation.
 *
 * @param email account email address
 * @param database context to use, including the registration transaction when provided
 * @return verification delivery details
 * @throws BadRequestException if the email is blank, too long, or already verified
 * @throws ResourceNotFoundException if the account does not exist
 * @throws IllegalStateException if required account data is missing or the token cannot be created
 */
fun AuthService.requestEmailVerification(
    email: String,
    database: DSLContext = Service.dsl
): EmailVerificationDelivery {
    val normalizedEmail = email.trim().lowercase()
    if (normalizedEmail.isBlank() || normalizedEmail.length > 255) {
        throw BadRequestException("A valid email address is required")
    }

    return database.transactionResult { configuration ->
        val tx = DSL.using(configuration)
        val user = tx.selectFrom(USERS)
            .where(DSL.lower(USERS.EMAIL).eq(normalizedEmail))
            .forUpdate()
            .fetchOne()
            ?: throw ResourceNotFoundException("The account could not be found")
        if (user.emailVerified == true) {
            throw BadRequestException("The email address is already verified")
        }

        val userId = user.id
        checkNotNull(userId) { "The account is missing an id" }
        val userEmail = user.email
        checkNotNull(userEmail) { "The account is missing an email address" }

        val token = CryptoService.generateRandomToken()
        check(tx.insertInto(EMAIL_VERIFICATION_TOKENS)
            .set(EMAIL_VERIFICATION_TOKENS.USER_ID, userId)
            .set(EMAIL_VERIFICATION_TOKENS.TOKEN_HASH, CryptoService.sha256(token))
            .set(EMAIL_VERIFICATION_TOKENS.EXPIRES_AT, OffsetDateTime.now().plusHours(24))
            .execute() == 1) { "The email verification token could not be created" }

        EmailVerificationDelivery(userEmail, token)
    }
}
