package rosemary_and_thyme.services

import com.auth0.jwt.algorithms.Algorithm
import com.password4j.Argon2Function
import com.password4j.types.Argon2
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.crypto.password4j.Argon2Password4jPasswordEncoder
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.HexFormat
import kotlin.io.encoding.Base64

/**
 * Provides cryptographic operations for passwords, session tokens, and JWT signing.
 * The RSA signing algorithm must be initialized from application configuration before issuing JWTs.
 */
object CryptoService {

    private const val MEMORY_KIB = 19 * 1024
    private const val ITERATIONS = 2
    private const val PARALLELISM = 1
    private const val HASH_LENGTH = 32
    private const val TOKEN_LENGTH_BYTES = 32
    private var jwtAlgorithmNullable: Algorithm? = null

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
     * RSA SHA-256 algorithm used to sign JWTs issued for the recipe media worker.
     *
     * @throws IllegalStateException if JWT signing has not been initialized
     */
    val jwtAlgorithm: Algorithm
        get() = checkNotNull(jwtAlgorithmNullable) { "JWT signing is not initialized" }

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


    /**
     * Initializes JWT signing from Base64-encoded RSA keys.
     * The private key must use PKCS#8 encoding and the public key X.509 encoding.
     *
     * @param publicKey Base64-encoded X.509 RSA public key
     * @param privateKey Base64-encoded PKCS#8 RSA private key
     * @throws IllegalStateException if JWT signing has already been initialized
     */
    fun initializeJwtAlgorithm(publicKey: String, privateKey: String) {
        check(jwtAlgorithmNullable == null) { "JWT signing has already been initialized" }
        val decoder = Base64
        val keyFactory = KeyFactory.getInstance("RSA")

        val privateKey = keyFactory.generatePrivate(PKCS8EncodedKeySpec(decoder.decode(privateKey))) as RSAPrivateKey

        val publicKey = keyFactory.generatePublic(X509EncodedKeySpec(decoder.decode(publicKey))) as RSAPublicKey

        jwtAlgorithmNullable = Algorithm.RSA256(publicKey, privateKey)
    }
}
