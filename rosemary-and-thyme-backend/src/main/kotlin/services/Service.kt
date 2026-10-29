package rosemary_and_thyme.services

import com.password4j.Argon2Function
import com.password4j.types.Argon2
import org.jooq.DSLContext
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.crypto.password4j.Argon2Password4jPasswordEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.HexFormat
import kotlin.io.encoding.Base64

/**
 * Central access point for application services and their shared [DSLContext].
 *
 * Service operations are organized as extension functions on [RecipeService], [UserService],
 * and [AuthService]. The object must be initialized before any database-backed operation runs.
 */
object Service {
    private var dslContext: DSLContext? = null

    val recipeService = RecipeService()
    val userService = UserService()
    val authService = AuthService()

    /**
     * The shared jOOQ context used by service operations.
     *
     * @throws IllegalStateException if [Service] has not been initialized
     */
    val dsl: DSLContext
        get() = checkNotNull(dslContext) { "Service has not been initialized" }

    /**
     * Initializes the service layer with the application's jOOQ context.
     *
     * @param dsl context used for database access
     * @throws IllegalStateException if the service layer is already initialized
     */
    fun initialize(dsl: DSLContext) {
        check(dslContext == null) {
            "Service has already been initialized"
        }
        dslContext = dsl
    }

    /** Clears the stored jOOQ context during application shutdown. */
    fun clear() {
        dslContext = null
    }
}


/** Namespace for recipe-related service operations. */
class RecipeService

/** Namespace for user account service operations. */
class UserService

/** Namespace for authentication and session service operations. */
class AuthService

