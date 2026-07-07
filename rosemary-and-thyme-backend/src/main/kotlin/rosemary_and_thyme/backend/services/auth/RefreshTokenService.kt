package rosemary_and_thyme.backend.services.auth

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rosemary_and_thyme.backend.models.JWTRefreshPair
import rosemary_and_thyme.backend.models.RotatedResponse
import rosemary_and_thyme.backend.repositories.auth.RefreshTokenRepository
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.LocalDateTime
import java.util.UUID
import kotlin.io.encoding.Base64

@Service
class RefreshTokenService(
     val refreshTokenRepository: RefreshTokenRepository,
     val jwtService: JwtService,
    @Value("\${refresh-token.ttl-days}") ttlDays: Long
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


    fun issue(userId: UUID, familyId: UUID = UUID.randomUUID()): String {
        val rawToken = generateSecureRandomToken()
        val tokenHash = sha256(rawToken)

        refreshTokenRepository.insertRefreshToken(userId, familyId, tokenHash)

        return rawToken

    }

    @Transactional
    fun rotate(rawToken: String): JWTRefreshPair {

        val tokenHash = sha256(rawToken)

        val token = refreshTokenRepository.selectRefreshToken(tokenHash)

        check(!token.revoked!!) { "Token revoked" }

        check(!token.used!!) { "Token already used" }

        check(token.expiresAt.isAfter(LocalDateTime.now())) { "Token expired" }

        val rowsUpdated = refreshTokenRepository.useRefreshToken(tokenHash)

        if (rowsUpdated == 0) {
            refreshTokenRepository.revokeRefreshTokenFamily(token.familyId)

            throw IllegalStateException(
                "Refresh token replay detected"
            )
        }

        val rotatedToken = issue(token.userId, token.familyId)
        val jwtToken = jwtService.generateJwtToken(token.userId)

        return JWTRefreshPair(rotatedToken,jwtToken)
    }

    @Transactional
    fun logoutUser(rawRefreshToken: String) {

        val tokenHash = sha256(rawRefreshToken)

        val token = refreshTokenRepository.selectRefreshToken(tokenHash)
        refreshTokenRepository.revokeRefreshTokenFamily(token.familyId)
    }
}