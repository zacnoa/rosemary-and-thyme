package rosemary_and_thyme.backend.exceptions

/** Thrown by [rosemary_and_thyme.backend.services.auth.SessionService.validateSession] when a session token is missing, revoked, or expired. */
class SessionException(message: String) : RuntimeException(message)
