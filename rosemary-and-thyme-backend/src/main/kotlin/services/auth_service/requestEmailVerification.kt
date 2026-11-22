package rosemary_and_thyme.services.auth_service

import io.ktor.server.plugins.BadRequestException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jooq.DSLContext
import org.jooq.impl.DSL
import rosemary_and_thyme.jooq.app_auth.tables.references.EMAIL_VERIFICATION_TOKENS
import rosemary_and_thyme.jooq.app_auth.tables.references.USERS
import rosemary_and_thyme.models.exceptions.ResourceNotFoundException
import rosemary_and_thyme.services.AuthService
import rosemary_and_thyme.services.CryptoService
import rosemary_and_thyme.services.Service
import java.time.OffsetDateTime
import java.util.UUID

private const val VERIFICATION_CODE_VALIDITY_MINUTES = 5L

/**
 * Holds the registration identifier and the details needed to send the code by email.
 *
 * @property email destination email address stored on the account
 * @property verificationId database-generated identifier returned to the registration client
 * @property code raw code sent only to the account's email address
 */
class EmailVerificationDelivery(val email: String, val verificationId: String, val code: String)

/**
 * Issues a five-minute verification code and stores its SHA-256 hash scoped to the account.
 * The user row is locked to coordinate issuance with email confirmation.
 *
 * @param email account email address
 * @param database context to use, including the registration transaction when provided
 * @return verification delivery details
 * @throws BadRequestException if the email is blank, too long, or already verified
 * @throws ResourceNotFoundException if the account does not exist
 * @throws IllegalStateException if required account data is missing or the verification record cannot be created
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

        val code = CryptoService.generateEmailVerificationCode()
        val verificationId = checkNotNull(
            tx.insertInto(EMAIL_VERIFICATION_TOKENS)
                .set(EMAIL_VERIFICATION_TOKENS.USER_ID, userId)
                .set(EMAIL_VERIFICATION_TOKENS.TOKEN_HASH, CryptoService.sha256("$userId:$code"))
                .set(EMAIL_VERIFICATION_TOKENS.EXPIRES_AT, OffsetDateTime.now().plusMinutes(VERIFICATION_CODE_VALIDITY_MINUTES))
                .returning(EMAIL_VERIFICATION_TOKENS.ID)
                .fetchOne(EMAIL_VERIFICATION_TOKENS.ID)
        ) { "The email verification record could not be created" }

        EmailVerificationDelivery(userEmail, verificationId.toString(), code)
    }
}

/**
 * Replaces the code for a pending registration and starts a new five-minute validity period.
 * The same verification ID can be used to request a replacement after the code expires.
 *
 * @param verificationId identifier returned when the registration started
 * @return delivery details for the replacement code
 * @throws BadRequestException if the verification ID does not identify a pending registration
 */
suspend fun AuthService.resendEmailVerification(verificationId: String): EmailVerificationDelivery {
    val recordId = runCatching { UUID.fromString(verificationId) }.getOrNull()
        ?: throw BadRequestException("Invalid email verification ID")

    return withContext(Dispatchers.IO) {
        Service.dsl.transactionResult { configuration ->
            val tx = DSL.using(configuration)
            val token = tx.selectFrom(EMAIL_VERIFICATION_TOKENS)
                .where(EMAIL_VERIFICATION_TOKENS.ID.eq(recordId))
                .and(EMAIL_VERIFICATION_TOKENS.USED_AT.isNull)
                .forUpdate()
                .fetchOne() ?: throw BadRequestException("Invalid email verification ID")

            val userId = checkNotNull(token.userId)
            val email = tx.select(USERS.EMAIL)
                .from(USERS)
                .where(USERS.ID.eq(userId))
                .and(USERS.EMAIL_VERIFIED.eq(false))
                .fetchOne(USERS.EMAIL)
                ?: throw BadRequestException("Invalid email verification ID")

            var code = CryptoService.generateEmailVerificationCode()
            var codeHash = CryptoService.sha256("$userId:$code")
            while (codeHash == token.tokenHash) {
                code = CryptoService.generateEmailVerificationCode()
                codeHash = CryptoService.sha256("$userId:$code")
            }

            token.tokenHash = codeHash
            token.expiresAt = OffsetDateTime.now().plusMinutes(VERIFICATION_CODE_VALIDITY_MINUTES)
            check(token.update() == 1) { "The email verification code could not be replaced" }

            EmailVerificationDelivery(email, recordId.toString(), code)
        }
    }
}
