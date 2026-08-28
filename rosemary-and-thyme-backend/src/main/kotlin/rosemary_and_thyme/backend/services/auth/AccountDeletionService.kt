package rosemary_and_thyme.backend.services.auth

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import rosemary_and_thyme.backend.repositories.auth.UserRepository
import rosemary_and_thyme.backend.repositories.dbNotNull
import rosemary_and_thyme.backend.services.recipes.RecipeService
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

/**
 * Owns "delete my account" end to end: requesting it, cancelling it within the grace
 * period, and the daily job that actually erases an account once that period has
 * passed.
 *
 * A flag-on-the-row design (`users.deletion_requested_at`, see db/schema.sql), not a
 * separate archive table - the point of a deletion request is that the data is
 * genuinely gone once the grace period ends, not moved somewhere else, so there is
 * nothing left to archive.
 *
 * The account stays fully usable during the 30-day grace period (login is not
 * blocked) - `UserDTO.deletionRequestedAt` lets the frontend show a persistent
 * "your account will be deleted in N days - cancel?" banner instead.
 */
@Service
class AccountDeletionService(
    private val userRepository: UserRepository,
    private val recipeService: RecipeService
) {
    private val log = LoggerFactory.getLogger(AccountDeletionService::class.java)

    private val gracePeriod = java.time.Duration.ofDays(30)

    /** Marks [userId]'s account for deletion 30 days from now. Requesting again just resets the 30-day clock. */
    fun requestDeletion(userId: UUID) {
        userRepository.updateDeletionRequestedAt(userId)
    }

    /** Cancels a pending deletion request. No-op if none was pending. */
    fun cancelDeletion(userId: UUID) {
        userRepository.clearDeletionRequestedAt(userId)
    }

    /**
     * Runs once a day (03:00 server time - low-traffic hours) and permanently erases
     * every account whose grace period has passed.
     *
     * Recipes are deleted first, one by one via [RecipeService.deleteAllRecipesForUser]
     * (which cleans up Cloudinary images too), then the user row itself - at that
     * point `ON DELETE CASCADE` (db/schema.sql) takes every session, oauth identity,
     * and email-verification/password-reset token with it.
     *
     * A failure partway through one account (e.g. a Cloudinary hiccup bubbling up,
     * though [rosemary_and_thyme.backend.services.recipes.RecipeService]'s own image
     * cleanup is already best-effort/non-throwing) is logged and does not stop the
     * remaining accounts in this run from being processed - one bad row shouldn't
     * block everyone else's deletion from actually completing.
     */
    @Scheduled(cron = "0 0 3 * * *")
    fun purgeExpiredAccounts() {
        val cutoff = OffsetDateTime.now(ZoneOffset.UTC).minus(gracePeriod)
        val dueUsers = userRepository.selectUsersPendingHardDelete(cutoff)

        dueUsers.forEach { user ->
            val userId = user.id.dbNotNull("id")
            try {
                recipeService.deleteAllRecipesForUser(user)
                userRepository.deleteUserById(userId)
            } catch (e: Exception) {
                log.error("Failed to purge account {} - will retry on the next run", userId, e)
            }
        }
    }
}
