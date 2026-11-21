package rosemary_and_thyme.models.requests

import kotlinx.serialization.Serializable

/**
 * Account data submitted to the registration endpoint.
 *
 * @property username requested display username
 * @property password plaintext password supplied by the client
 * @property email email address for the new account
 */
@Serializable
data class RegisterRequest(val username:String,val password:String,val email:String)


