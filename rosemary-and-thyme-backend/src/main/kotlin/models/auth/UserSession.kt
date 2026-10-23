package rosemary_and_thyme.models.auth
import kotlinx.serialization.Serializable


@Serializable
data class UserSession(val cookieHash:String)