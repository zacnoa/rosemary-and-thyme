package rosemary_and_thyme.services.user_service

import rosemary_and_thyme.jooq.app_auth.tables.references.USERS
import rosemary_and_thyme.models.user.UserPrincipal
import rosemary_and_thyme.services.Service
import rosemary_and_thyme.services.UserService
import java.time.OffsetDateTime


/**
 * Schedules the authenticated user's account for deletion.
 *
 * @param user user whose deletion timestamp should be set
 * @return the number of updated rows
 * @throws IllegalStateException if exactly one account was not updated
 */
fun UserService.requestDelete(user: UserPrincipal) =
    
   Service.dsl.update(USERS)
        .set(USERS.DELETION_REQUESTED_AT, OffsetDateTime.now())
        .where(USERS.ID.eq(user.id))
       .execute().also{affectedRows->
           check(affectedRows==1){
               "Expected one affecter row, but updated $affectedRows"
           }
       }



/**
 * Cancels a pending account-deletion request.
 *
 * @param user user whose deletion timestamp should be cleared
 * @return the number of updated rows
 * @throws IllegalStateException if exactly one account was not updated
 */
fun UserService.cancelDelete(user:UserPrincipal) =
   Service.dsl.update(USERS)
        .setNull(USERS.DELETION_REQUESTED_AT)
        .where(USERS.ID.eq(user.id))
       .execute().also{affectedRows->
           check(affectedRows==1){
               "Expected one affecter row, but updated $affectedRows"
           }
       }

