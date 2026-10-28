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

object Service {
    private var dslContext: DSLContext? = null

    val recipeService = RecipeService()
    val userService = UserService()
    val authService = AuthService()

    val dsl: DSLContext
        get() = checkNotNull(dslContext) { "Service has not been initialized" }

    fun initialize(dsl: DSLContext) {
        check(dslContext == null) {
            "Service has already been initialized"
        }
        dslContext = dsl
    }

    fun clear() {
        dslContext = null
    }
}


class RecipeService
class UserService
class AuthService

