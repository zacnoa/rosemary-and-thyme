package rosemary_and_thyme.services.cron_service

import rosemary_and_thyme.jooq.app_auth.tables.references.USERS
import rosemary_and_thyme.services.CronService
import rosemary_and_thyme.services.Service
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * Deletes accounts whose 30-day cancellation period has expired.
 *
 * @return the number of accounts deleted from the database
 */
fun CronService.deleteRequestedUsers(): Int {
    val cutoff = OffsetDateTime.now(ZoneOffset.UTC).minusDays(30)

    return Service.dsl.deleteFrom(USERS)
        .where(USERS.DELETION_REQUESTED_AT.le(cutoff))
        .execute()
}
