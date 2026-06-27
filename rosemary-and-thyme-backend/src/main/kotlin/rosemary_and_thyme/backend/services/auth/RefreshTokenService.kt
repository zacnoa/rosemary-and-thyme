package rosemary_and_thyme.backend.services.auth

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rosemary_and_thyme.backend.models.RawRefreshToken
import rosemary_and_thyme.backend.repositories.auth.RefreshTokenRepository
import java.nio.charset.StandardCharsets
import java.security.InvalidKeyException
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.LocalDateTime
import java.util.UUID
import kotlin.io.encoding.Base64

@Service
class RefreshTokenService(
    val refreshTokenRepository: RefreshTokenRepository,
    @Value($$"${refresh-token.ttl-days") ttlDays: Long
) {

    private fun generateSecureRandomToken(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(bytes)
    }

    private fun sha256(input: String) =
        MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }


    fun issue(userId: UUID, familyId: UUID = UUID.randomUUID()): RawRefreshToken {
        val rawToken = generateSecureRandomToken()
        val tokenHash = sha256(rawToken)

        refreshTokenRepository.insertRefreshToken(userId, familyId, tokenHash);

        return RawRefreshToken(tokenHash, familyId)

    }

    @Transactional
    fun rotate(rawToken: String, userId: UUID): RawRefreshToken {

        val tokenHash = sha256(rawToken)
        val token = refreshTokenRepository.selectRefreshToken(tokenHash)

        check(token.revoked!!) { "Token is expired!" }

        check(token.expiresAt.isBefore(LocalDateTime.now())) { "Token expired!" }
        check(token.userId == userId) { "Token user id does not match user ID" }

        if (token.used == true) {
            refreshTokenRepository.revokeRefreshTokenFamily(token.familyId)
            throw IllegalStateException("Token has been previously  used")
        }

        refreshTokenRepository.useRefreshToken(tokenHash)

        return issue(token.userId, token.familyId)
    }


}