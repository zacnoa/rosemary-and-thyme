package rosemary_and_thyme.models.requests

/**
 * Account data submitted to the registration endpoint.
 *
 * @property username requested display username
 * @property password plaintext password supplied by the client
 * @property email email address for the new account
 */
data class RegisterRequest(val username:String,val password:String,val email:String)


