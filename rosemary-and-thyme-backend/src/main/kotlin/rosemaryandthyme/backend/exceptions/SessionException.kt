package rosemaryandthyme.backend.exceptions

/** Thrown by [rosemaryandthyme.backend.services.auth.SessionService.validateSession] when a session token is missing, revoked, or expired. */
class SessionException(message: String) : RuntimeException(message)
