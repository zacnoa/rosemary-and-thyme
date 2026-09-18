package rosemaryandthyme.backend

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

// EnableScheduling: required for AccountDeletionService.purgeExpiredAccounts' @Scheduled
// job to actually run - Spring Boot doesn't enable the scheduler infrastructure by default.
@EnableScheduling
@SpringBootApplication
class RosemaryAndThymeBackendApplication

fun main(args: Array<String>) {
    runApplication<RosemaryAndThymeBackendApplication>(*args)
}
