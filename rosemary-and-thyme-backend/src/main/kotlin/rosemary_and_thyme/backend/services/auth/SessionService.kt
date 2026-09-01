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


/**
 * Generates a fresh, unguessable session token.
 *
 * Uses [SecureRandom] (a CSPRNG, unlike [kotlin.random.Random]) to fill 32 bytes
 * (256 bits) - far more entropy than an attacker could ever brute-force - then
 * base64-encodes them so the token is safe to use as a cookie value and URL query
 * parameter (see [EmailVerificationService], which reuses this same function for
 * verification tokens). Padding is stripped since it adds no entropy and cookies/URLs
 * have no need for a fixed-length encoding.
 *
 * @return a new raw (unhashed) token, safe to hand to the client
 */
fun generateSessionToken(): String {
    val randomBytes = ByteArray(32)
    SecureRandom().nextBytes(randomBytes)
    return Base64.withPadding(Base64.PaddingOption.ABSENT).encode(randomBytes)
}

/**
 * One-way hashes a token for storage.
 *
 * Only this hash is ever persisted (in `app_auth.sessions.session_hash` or
 * `app_auth.email_verification_tokens.token_hash`) - never the raw token - so that a
 * database read/leak alone can't be used to impersonate a session or verification
 * link; the raw token, which only ever exists in the client's cookie/URL, is still
 * required to hash-match a row. SHA-256 (not bcrypt/argon2) is appropriate here
 * because the input is already a full 256 bits of high-entropy randomness rather than
 * a low-entropy human password, so a slow, salted KDF buys nothing against brute force.
 *
 * @param token the raw token to hash
 * @return the token's SHA-256 hash, as a lowercase hex string
 */
fun hashToken(token: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val hashBytes = digest.digest(token.toByteArray(Charsets.UTF_8))
    return hashBytes.joinToString("") { "%02x".format(it) }
}


/**
 * Owns the full lifecycle of a login session: issuing a token, validating it on every
 * authenticated request, and revoking it on logout. A session is just a random,
 * hashed, expiring bearer token row in `app_auth.sessions` - there is no server-side
 * session store beyond that table, so validating a session is always a DB round trip
 * (see [validateSession]). Consumed by [rosemary_and_thyme.backend.filters.SessionAuthFilter]
 * on every request and by [AuthController][rosemary_and_thyme.backend.controllers.AuthController]
 * on login/logout.
 */
@Service
class SessionService(
    val sessionRepository: SessionRepository,
    val userRepository: UserRepository
) {
    private val log = LoggerFactory.getLogger(SessionService::class.java)

    /**
     * Starts a new session for a user (called after a successful login, registration
     * email verification, etc). Only the hash is persisted; the raw token is returned
     * so the caller can hand it to the client as a cookie value.
     *
     * @param userId the user the session belongs to
     * @return a raw session token
     */

    fun createSession(userId: UUID): String {

        val rawToken = generateSessionToken()
        val hashedToken = hashToken(rawToken)

        sessionRepository.insertSession(userId, hashedToken)
        return rawToken
    }


    /**
     * Validates a session cookie and resolves it to its owning user. Called on every
     * authenticated request (via [rosemary_and_thyme.backend.filters.SessionAuthFilter]),
     * so this is the single choke point that enforces "is this request logged in".
     *
     * @param sessionToken the raw session token from the client's cookie
     * @return the session's owner
     * @throws SessionException if no session matches the token's hash, the session was
     * revoked (e.g. logged out elsewhere), or the session has expired
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

    /**
     * Ends a session (logout). Looks the session up by hash first rather than
     * deleting/updating by token directly so a no-op below can short-circuit cleanly.
     *
     * No-ops for a token that doesn't match any session (already logged out, expired
     * and cleaned up, or just a stale/tampered cookie) - logging out twice should never
     * be an error.
     *
     * @param sessionToken the raw session token to revoke
     */
    fun revokeSession(sessionToken: String) {
        val hashedToken = hashToken(sessionToken)

        val session = sessionRepository.selectSessionByHash(hashedToken) ?: return

        sessionRepository.updateSessionRevoked(session.id.dbNotNull("id"))
    }

    /**
     * Revokes every session a user has, on any device - called after a password
     * change/reset (see [rosemary_and_thyme.backend.services.auth.AuthService.changePassword]/
     * [rosemary_and_thyme.backend.services.auth.AuthService.resetPassword]) so the old
     * credential can't keep an existing session alive elsewhere. The caller is
     * expected to immediately issue a fresh session (via [createSession]) for the
     * request that triggered this, since that request's own session cookie is
     * revoked along with everything else.
     *
     * @param userId the user whose sessions should all be revoked
     */
    fun revokeAllSessions(userId: UUID) {
        sessionRepository.updateAllSessionsRevokedByUserId(userId)
    }

    /**
     * Runs once a day (03:30 server time, right after
     * [AccountDeletionService.purgeExpiredAccounts]'s own 03:00 job so the two never
     * contend for the same low-traffic window) and hard-deletes every session row that
     * is revoked, expired, or both (see [SessionRepository.deleteInvalidSessions]).
     * Purely a housekeeping job - a revoked or expired session already fails
     * [validateSession] and authenticates nothing, so this changes no observable
     * behavior; it only keeps `app_auth.sessions` from accumulating rows nothing will
     * ever look up again (every login/verify/reset inserts a fresh one, and none of
     * those flows ever delete the old ones themselves - only [revokeSession]/
     * [revokeAllSessions] mark them revoked).
     */
    @Scheduled(cron = "0 30 3 * * *")
    fun purgeInvalidSessions() {
        val deleted = sessionRepository.deleteInvalidSessions()
        log.info("Purged {} invalid session(s)", deleted)
    }

}
