package rosemary_and_thyme.backend.exceptions

/**
 * Thrown by [rosemary_and_thyme.backend.services.auth.SessionService.validateSession]
 * when a session token is missing, revoked, or expired. Mapped to 403 by
 * [rosemary_and_thyme.backend.controllers.GlobalExceptionHandler.handleSession].
 *
 * @param message a specific reason (missing/revoked/expired/owner not found), for logs -
 * the client only ever sees the mapped problem-detail response
 */
class SessionException(message: String) : RuntimeException(message)
