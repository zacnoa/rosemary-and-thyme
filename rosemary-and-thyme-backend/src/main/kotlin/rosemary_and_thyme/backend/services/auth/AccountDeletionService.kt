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

/** Schedules, cancels, and permanently purges account deletions. */
@Service
class AccountDeletionService(
    private val userRepository: UserRepository,
    private val recipeService: RecipeService
) {
    private val log = LoggerFactory.getLogger(AccountDeletionService::class.java)

    private val gracePeriod = java.time.Duration.ofDays(30)

    /** Starts or renews an account's deletion grace period.
     * @param userId the account to schedule for deletion.
     */
    fun requestDeletion(userId: UUID) {
        userRepository.updateDeletionRequestedAt(userId)
    }

    /** Cancels an account's pending deletion request.
     * @param userId the account to retain.
     */
    fun cancelDeletion(userId: UUID) {
        userRepository.clearDeletionRequestedAt(userId)
    }

    /** Permanently deletes accounts whose grace period has expired. */
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
