package rosemary_and_thyme.backend.repositories.auth

import org.jooq.DSLContext
import org.springframework.dao.DataAccessException
import org.springframework.stereotype.Repository
import rosemary_and_thyme.backend.exceptions.UserAlreadyExistsException
import rosemary_and_thyme.backend.jooqgenerated.app_auth.tables.records.UsersRecord
import rosemary_and_thyme.backend.jooqgenerated.app_auth.tables.references.USERS
import java.sql.SQLException
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

@Repository
class UserRepository(val dsl: DSLContext) {

    /** @return the user with this email, or `null` if none exists */
    fun selectUserByEmail(email: String): UsersRecord? =
        dsl.selectFrom(USERS)
            .where(USERS.EMAIL.eq(email))
            .fetchOne()

    /**
     * Inserts a new, unverified user.
     *
     * @param email must be unique; relies on the DB's unique constraint
     * (`users_email_key`) rather than a prior existence check, so this can't race
     * (see the caught [DataAccessException] below)
     * @param password the already-hashed (BCrypt) password - never plaintext
     * @param userName the display name
     * @return the newly inserted row
     * @throws UserAlreadyExistsException if [email] is already registered (translated
     * from the Postgres unique-violation SQLSTATE `23505`)
     */
    //TODO: add existing email check
    fun insertUser(email: String, password: String, userName: String): UsersRecord {

        try {

            return dsl.insertInto(USERS)
                .set(USERS.EMAIL, email)
                .set(USERS.PASSWORD, password)
                .set(USERS.USERNAME, userName)
                .returning()
                .fetchOne() ?: throw IllegalStateException("Failed to insert user")
        } catch (ex: DataAccessException) {
            val sqlEx = ex.cause as? SQLException
            if (sqlEx?.sqlState == "23505") {
                throw UserAlreadyExistsException("A user with that email already exists")
            }
            throw ex
        }
    }

    /**
     * Inserts a new, already-verified user with no password - the Google sign-in
     * counterpart of [insertUser] (see
     * [rosemary_and_thyme.backend.services.auth.GoogleAuthService.resolveOrCreateUser],
     * the only caller). Google has already verified the email, and there's no
     * password to hash since an account like this only ever authenticates through
     * Google - see [rosemary_and_thyme.backend.services.auth.AuthService.login]'s
     * null-password guard for what stops a password login attempt against it.
     *
     * @param email must be unique, same constraint/race handling as [insertUser]
     * @param userName the display name, taken from the Google profile
     * @return the newly inserted row
     * @throws UserAlreadyExistsException if [email] is already registered
     */
    fun insertOauthUser(email: String, userName: String): UsersRecord {

        try {

            return dsl.insertInto(USERS)
                .set(USERS.EMAIL, email)
                .set(USERS.USERNAME, userName)
                .set(USERS.EMAIL_VERIFIED, true)
                .returning()
                .fetchOne() ?: throw IllegalStateException("Failed to insert user")
        } catch (ex: DataAccessException) {
            val sqlEx = ex.cause as? SQLException
            if (sqlEx?.sqlState == "23505") {
                throw UserAlreadyExistsException("A user with that email already exists")
            }
            throw ex
        }
    }

    /** @return the user with this id, or `null` if none exists */
    fun selectUserById(userId: UUID): UsersRecord? =
        dsl.selectFrom(USERS)
            .where(USERS.ID.eq(userId))
            .fetchOne()

    /** Flips a user's `email_verified` flag to `true`. Called once, from [rosemary_and_thyme.backend.services.auth.EmailVerificationService.verify]. */
    fun updateEmailVerified(userId: UUID) {
        dsl.update(USERS)
            .set(USERS.EMAIL_VERIFIED, true)
            .where(USERS.ID.eq(userId))
            .execute()
    }

    /** Overwrites a user's stored password hash - used by both the authenticated change-password flow and the emailed reset-token flow. @param passwordHash the new, already-hashed (BCrypt) password */
    fun updatePassword(userId: UUID, passwordHash: String) {
        dsl.update(USERS)
            .set(USERS.PASSWORD, passwordHash)
            .where(USERS.ID.eq(userId))
            .execute()
    }

    /** Marks a user's account for deletion 30 days from now (see [UsersRecord.deletionRequestedAt]'s column comment in db/schema.sql). */
    fun updateDeletionRequestedAt(userId: UUID) {
        dsl.update(USERS)
            .set(USERS.DELETION_REQUESTED_AT, OffsetDateTime.now(ZoneOffset.UTC))
            .where(USERS.ID.eq(userId))
            .execute()
    }

    /** Cancels a pending deletion request (the grace-period "undo"). No-op if none was pending. */
    fun clearDeletionRequestedAt(userId: UUID) {
        dsl.update(USERS)
            .setNull(USERS.DELETION_REQUESTED_AT)
            .where(USERS.ID.eq(userId))
            .execute()
    }

    /**
     * @param cutoff accounts whose `deletion_requested_at` is older than this are due
     * @return every account whose grace period has expired - see
     * [rosemary_and_thyme.backend.services.auth.AccountDeletionService.purgeExpiredAccounts],
     * the only caller
     */
    fun selectUsersPendingHardDelete(cutoff: OffsetDateTime): List<UsersRecord> =
        dsl.selectFrom(USERS)
            .where(USERS.DELETION_REQUESTED_AT.isNotNull)
            .and(USERS.DELETION_REQUESTED_AT.lt(cutoff))
            .fetch()

    /**
     * Hard-deletes a user row. `ON DELETE CASCADE` takes every session, oauth
     * identity, and email-verification/password-reset token with it - recipes are
     * expected to already be gone by this point (see
     * [rosemary_and_thyme.backend.services.auth.AccountDeletionService.purgeExpiredAccounts],
     * which deletes them one by one first so their Cloudinary images are cleaned up
     * too, something a raw cascade here can't do).
     */
    fun deleteUserById(userId: UUID) {
        dsl.deleteFrom(USERS)
            .where(USERS.ID.eq(userId))
            .execute()
    }

}
