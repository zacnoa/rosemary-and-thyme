package rosemary_and_thyme.backend.services.auth

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import rosemary_and_thyme.backend.exceptions.SessionException
import rosemary_and_thyme.backend.jooqgenerated.app_auth.tables.records.UsersRecord
import rosemary_and_thyme.backend.repositories.auth.SessionRepository
import rosemary_and_thyme.backend.repositories.auth.UserRepository
import rosemary_and_thyme.backend.repositories.dbNotNull
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlin.io.encoding.Base64


/** Generates a cryptographically secure session token.
 * @return a URL-safe random token.
 */
fun generateSessionToken(): String {
    val randomBytes = ByteArray(32)
    SecureRandom().nextBytes(randomBytes)
    return Base64.withPadding(Base64.PaddingOption.ABSENT).encode(randomBytes)
}

/** Hashes a token for database storage.
 * @param token the raw session or verification token.
 * @return the token's SHA-256 hash as hexadecimal text.
 */
fun hashToken(token: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val hashBytes = digest.digest(token.toByteArray(Charsets.UTF_8))
    return hashBytes.joinToString("") { "%02x".format(it) }
}


/** Manages application session creation, validation, revocation, and cleanup. */
@Service
class SessionService(
    val sessionRepository: SessionRepository,
    val userRepository: UserRepository
) {
    private val log = LoggerFactory.getLogger(SessionService::class.java)

    /** Creates a new session for a user.
     * @param userId the account receiving the session.
     * @return the raw token that is sent to the client.
     */

    fun createSession(userId: UUID): String {

        val rawToken = generateSessionToken()
        val hashedToken = hashToken(rawToken)

        sessionRepository.insertSession(userId, hashedToken)
        return rawToken
    }


    /** Validates a session token and resolves its owner.
     * @param sessionToken the raw token from the session cookie.
     * @return the authenticated user.
     * @throws SessionException if the session is missing, revoked, expired, or orphaned.
     */
    fun validateSession(sessionToken: String): UsersRecord {
        val hashedToken = hashToken(sessionToken)

        val session = sessionRepository.selectSessionByHash(hashedToken)
            ?: throw SessionException("Session with this id does not exist")

        if (session.revoked == true) {
            throw SessionException("Session has been revoked")
        }

        if (session.expiresAt.dbNotNull("expiresAt").isBefore(OffsetDateTime.now(ZoneOffset.UTC))) {
            throw SessionException("Session is expired")
        }

        val user = userRepository.selectUserById(session.userId)
            ?: throw SessionException("The owner of this session does not exist")

        return user
    }

    /** Revokes the session represented by a token.
     * @param sessionToken the raw token to revoke.
     */
    fun revokeSession(sessionToken: String) {
        val hashedToken = hashToken(sessionToken)

        val session = sessionRepository.selectSessionByHash(hashedToken) ?: return

        sessionRepository.updateSessionRevoked(session.id.dbNotNull("id"))
    }

    /** Revokes every active session for a user.
     * @param userId the user whose sessions are revoked.
     */
    fun revokeAllSessions(userId: UUID) {
        sessionRepository.updateAllSessionsRevokedByUserId(userId)
    }

    /** Deletes revoked and expired sessions once per day. */
    @Scheduled(cron = "0 30 3 * * *")
    fun purgeInvalidSessions() {
        val deleted = sessionRepository.deleteInvalidSessions()
        log.info("Purged {} invalid session(s)", deleted)
    }

}
