package rosemary_and_thyme.models.requests

import kotlinx.serialization.Serializable

/**
 * Credentials submitted to the login endpoint.
 *
 * @property email account email address
 * @property password plaintext password supplied by the client
 */
@Serializable
data class LoginRequest(val email:String,val password:String)
