package rosemary_and_thyme.backend.services.auth

import org.springframework.stereotype.Service
import rosemary_and_thyme.backend.exceptions.SessionException
import rosemary_and_thyme.backend.jooqgenerated.auth.tables.records.UsersRecord
import rosemary_and_thyme.backend.repositories.auth.SessionRepository
import rosemary_and_thyme.backend.repositories.auth.UserRepository
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlin.io.encoding.Base64




fun generateSessionToken(): String {
    val randomBytes = ByteArray(32)
    SecureRandom().nextBytes(randomBytes)
    return Base64.withPadding(Base64.PaddingOption.ABSENT).encode(randomBytes)
}

fun hashToken(token: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val hashBytes = digest.digest(token.toByteArray(Charsets.UTF_8))
    return hashBytes.joinToString("") { "%02x".format(it) }
}


@Service
class SessionService(
    val sessionRepository: SessionRepository,
    val userRepository: UserRepository
) {

    /**
     * @return  a raw session token
     */

    fun createSession(userId: UUID): String {

        val rawToken = generateSessionToken()
        val hashedToken = hashToken(rawToken)

        sessionRepository.insertSession(userId, hashedToken)
        return rawToken
    }


    fun validateSession(sessionToken: String): UsersRecord {
        val hashedToken = hashToken(sessionToken)

        val session = sessionRepository.fetchSession(hashedToken)
            ?: throw SessionException("Session with this id does not exist")

        if (session.expiresAt!!.isBefore(OffsetDateTime.now(ZoneOffset.UTC))) {
            throw SessionException("Session is expired")
        }

        val user = userRepository.selectUserById(session.userId)
            ?: throw SessionException("The owner of this session does not exist")

        return user
    }

    fun revokeSession(sessionToken: String) {
        val hashedToken=hashToken(sessionToken)

        val session=sessionRepository.fetchSession(hashedToken)

        sessionRepository.revokeSession(session!!.id!!)
    }


}