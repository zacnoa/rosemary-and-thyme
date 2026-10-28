package rosemary_and_thyme.services.auth_service

import rosemary_and_thyme.jooq.app_auth.tables.references.USERS
import rosemary_and_thyme.models.exceptions.UserAlreadyExists
import rosemary_and_thyme.services.AuthService
import rosemary_and_thyme.services.Service.cryptoService
import rosemary_and_thyme.services.Service.dsl


fun AuthService.registerUser(username:String,password:String,email:String){
    
    val normalizedUsername=username.trim().lowercase()
    val hashedPassword=cryptoService.hash(password)
    
    dsl.insertInto(USERS)
    .set(USERS.USERNAME,username)
    .set(USERS.USERNAME_NORMALIZED,normalizedUsername)
    .set(USERS.EMAIL,email)
    .set(USERS.PASSWORD,hashedPassword)
    .onConflict()
    .doNothing()
    .execute().also { affectedRows->
        if(affectedRows==0) throw UserAlreadyExists("A user with that username already exists")
     }

    
    

}