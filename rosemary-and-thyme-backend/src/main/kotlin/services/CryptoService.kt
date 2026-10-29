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

/**
 * Provides the cryptographic operations used for passwords and session tokens.
 * Passwords are encoded with Argon2id, while session tokens are generated with [SecureRandom].
 */
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
    
    /**
     * Encodes a password with Argon2id and a randomly generated salt.
     *
     * @param password plaintext password to encode
     * @return an encoded password containing the algorithm parameters and salt
     */
    fun hashPassword(password: String): String =
        checkNotNull(passwordEncoder.encode(password))

    /**
     * Verifies a plaintext password against an Argon2id-encoded password.
     *
     * @param password plaintext password to verify
     * @param passwordHash previously encoded password
     * @return `true` when the password matches the encoded value
     */
    fun matchesPassword(password: String, passwordHash: String): Boolean =
        passwordEncoder.matches(password, passwordHash)

    /**
     * Computes the SHA-256 digest of a UTF-8 string.
     *
     * @param value value to hash
     * @return the digest as a lowercase hexadecimal string
     */
    fun sha256(value: String): String {
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(StandardCharsets.UTF_8))

        return HexFormat.of().formatHex(bytes)
    }

    /**
     * Generates a cryptographically secure 256-bit session token.
     *
     * @return a URL-safe Base64 token without padding
     */
    fun generateRandomToken(): String {
        val tokenBytes = ByteArray(TOKEN_LENGTH_BYTES)
        secureRandom.nextBytes(tokenBytes)
        return tokenEncoder.encode(tokenBytes)
    }
}
