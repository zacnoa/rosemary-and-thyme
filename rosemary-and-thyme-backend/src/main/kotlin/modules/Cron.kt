package rosemary_and_thyme.modules

import io.ktor.server.application.Application
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.jooq.exception.DataAccessException
import rosemary_and_thyme.services.Service
import rosemary_and_thyme.services.cron_service.deleteRequestedUsers
import kotlin.time.Duration.Companion.days

/** Runs account cleanup at startup and then daily while the application is running. */
fun Application.configureCron() {
    launch(Dispatchers.IO) {
        while (isActive) {
            runCatching { Service.cronService.deleteRequestedUsers() }
                .onFailure { error ->
                    if (error !is DataAccessException) throw error
                }

            delay(1.days)
        }
    }
}
