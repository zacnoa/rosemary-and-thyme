package rosemary_and_thyme.services.auth_service

import rosemary_and_thyme.jooq.app_auth.tables.references.USERS
import rosemary_and_thyme.models.exceptions.UserAlreadyExists
import rosemary_and_thyme.services.AuthService
import rosemary_and_thyme.services.CryptoService
import rosemary_and_thyme.services.Service.dsl


/**
 * Registers a user while preserving the supplied username for display.
 * A trimmed, lowercase username is stored separately for uniqueness checks.
 *
 * @param username display name selected by the user
 * @param password plaintext password to encode before storage
 * @param email email address associated with the account
 * @throws UserAlreadyExists if the insert conflicts with an existing user
 */
fun AuthService.registerUser(username:String,password:String,email:String){
    
    val normalizedUsername=username.trim().lowercase()
    val hashedPassword= CryptoService.hashPassword(password)
    
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
