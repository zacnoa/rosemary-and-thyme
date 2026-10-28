package rosemary_and_thyme.models.requests

import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(val email:String,val password:String)
