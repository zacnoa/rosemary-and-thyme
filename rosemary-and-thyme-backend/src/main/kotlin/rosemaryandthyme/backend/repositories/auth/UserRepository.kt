package rosemaryandthyme.backend.repositories.auth

import org.jooq.DSLContext
import org.springframework.dao.DataAccessException
import org.springframework.stereotype.Repository
import rosemaryandthyme.backend.exceptions.UserAlreadyExistsException
import rosemaryandthyme.backend.jooqgenerated.app_auth.tables.records.UsersRecord
import rosemaryandthyme.backend.jooqgenerated.app_auth.tables.references.USERS
import java.sql.SQLException
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

/** Provides database operations for application users. */
@Repository
class UserRepository(val dsl: DSLContext) {

    /** Finds a user by email.
     * @param email the email address to look up.
     * @return the matching user, or `null` when none exists.
     */
    fun selectUserByEmail(email: String): UsersRecord? =
        dsl.selectFrom(USERS)
            .where(USERS.EMAIL.eq(email))
            .fetchOne()

    /** Inserts a password-based user with an unverified email.
     * @param email the unique account email.
     * @param password the already-hashed password.
     * @param userName the display name.
     * @return the inserted user.
     * @throws UserAlreadyExistsException if the email is already registered.
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

    /** Inserts a verified user without a password for OAuth sign-in.
     * @param email the unique account email.
     * @param userName the display name from the provider.
     * @return the inserted user.
     * @throws UserAlreadyExistsException if the email is already registered.
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

    /** Finds a user by id.
     * @param userId the user id to look up.
     * @return the matching user, or `null` when none exists.
     */
    fun selectUserById(userId: UUID): UsersRecord? =
        dsl.selectFrom(USERS)
            .where(USERS.ID.eq(userId))
            .fetchOne()

    /** Marks a user's email as verified.
     * @param userId the user to update.
     */
    fun updateEmailVerified(userId: UUID) {
        dsl.update(USERS)
            .set(USERS.EMAIL_VERIFIED, true)
            .where(USERS.ID.eq(userId))
            .execute()
    }

    /** Replaces a user's stored password hash.
     * @param userId the user to update.
     * @param passwordHash the new BCrypt hash.
     */
    fun updatePassword(userId: UUID, passwordHash: String) {
        dsl.update(USERS)
            .set(USERS.PASSWORD, passwordHash)
            .where(USERS.ID.eq(userId))
            .execute()
    }

    /** Records that a user requested account deletion.
     * @param userId the user to mark for deletion.
     */
    fun updateDeletionRequestedAt(userId: UUID) {
        dsl.update(USERS)
            .set(USERS.DELETION_REQUESTED_AT, OffsetDateTime.now(ZoneOffset.UTC))
            .where(USERS.ID.eq(userId))
            .execute()
    }

    /** Cancels a pending account deletion request.
     * @param userId the user whose deletion request is cleared.
     */
    fun clearDeletionRequestedAt(userId: UUID) {
        dsl.update(USERS)
            .setNull(USERS.DELETION_REQUESTED_AT)
            .where(USERS.ID.eq(userId))
            .execute()
    }

    /** Lists users whose deletion grace period has elapsed.
     * @param cutoff the latest deletion-request timestamp eligible for removal.
     * @return users scheduled for permanent deletion.
     */
    fun selectUsersPendingHardDelete(cutoff: OffsetDateTime): List<UsersRecord> =
        dsl.selectFrom(USERS)
            .where(USERS.DELETION_REQUESTED_AT.isNotNull)
            .and(USERS.DELETION_REQUESTED_AT.lt(cutoff))
            .fetch()

    /** Permanently deletes a user row.
     * @param userId the user to delete.
     */
    fun deleteUserById(userId: UUID) {
        dsl.deleteFrom(USERS)
            .where(USERS.ID.eq(userId))
            .execute()
    }

}
