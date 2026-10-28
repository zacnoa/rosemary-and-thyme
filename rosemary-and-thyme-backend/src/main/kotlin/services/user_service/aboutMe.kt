package rosemary_and_thyme.services.user_service

import rosemary_and_thyme.jooq.app_auth.tables.references.USERS
import rosemary_and_thyme.models.exceptions.ResourceNotFoundException
import rosemary_and_thyme.models.user.UserDTO
import rosemary_and_thyme.services.Service
import rosemary_and_thyme.services.Service.dsl
import rosemary_and_thyme.services.UserService
import java.util.UUID


fun UserService.aboutMe(id: UUID): UserDTO=
    
    dsl.select(USERS.ID,USERS.USERNAME,USERS.EMAIL)
    .from(USERS)
    .where(USERS.ID.eq(id))
    .fetchOne()?.let{record->
        UserDTO(
            id=checkNotNull(record.getValue(USERS.ID)).toString(),
            username = checkNotNull(record.getValue(USERS.USERNAME)),
            email=checkNotNull(record.getValue(USERS.EMAIL))
        )

    } ?: throw ResourceNotFoundException("User with this id does not exist") 
