package rosemary_and_thyme.services

import com.password4j.Argon2Function
import com.password4j.types.Argon2
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.crypto.password4j.Argon2Password4jPasswordEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.HexFormat
import kotlin.io.encoding.Base64

object CryptoService {

    private const val MEMORY_KIB = 19 * 1024
    private const val ITERATIONS = 2
    private const val PARALLELISM = 1
    private const val HASH_LENGTH = 32
    private const val TOKEN_LENGTH_BYTES = 32

    private val secureRandom = SecureRandom()

    private val tokenEncoder =
        Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)

    private val passwordEncoder: PasswordEncoder =
        Argon2Password4jPasswordEncoder(
            Argon2Function.getInstance(
                MEMORY_KIB,
                ITERATIONS,
                PARALLELISM,
                HASH_LENGTH,
                Argon2.ID
            )
        )

    fun hashPassword(password: String): String =
        checkNotNull(passwordEncoder.encode(password))

    fun matchesPassword(password: String, passwordHash: String): Boolean =
        passwordEncoder.matches(password, passwordHash)

    fun sha256(value: String): String {
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(StandardCharsets.UTF_8))

        return HexFormat.of().formatHex(bytes)
    }

    fun generateRandomToken(): String {
        val tokenBytes = ByteArray(TOKEN_LENGTH_BYTES)
        secureRandom.nextBytes(tokenBytes)
        return tokenEncoder.encode(tokenBytes)
    }
}