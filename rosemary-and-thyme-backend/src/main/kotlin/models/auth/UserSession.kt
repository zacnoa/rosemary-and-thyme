package rosemary_and_thyme.models.auth
import kotlinx.serialization.Serializable


/**
 * Client-side session data managed by Ktor's Sessions plugin.
 *
 * @property cookie raw token used to locate the corresponding database session
 * @see rosemary_and_thyme.modules.configureSession
 */
@Serializable
data class UserSession(val cookie:String)
