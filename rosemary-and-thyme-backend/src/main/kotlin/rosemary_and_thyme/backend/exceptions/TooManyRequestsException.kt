package rosemary_and_thyme.backend.exceptions

/**
 * Thrown when a caller exceeds one of the per-identity limits enforced via
 * [rosemary_and_thyme.backend.services.ratelimit.RateLimiterService] - e.g. login
 * attempts ([rosemary_and_thyme.backend.services.auth.AuthService.login]) or
 * verification-email resends
 * ([rosemary_and_thyme.backend.services.auth.EmailVerificationService.resend]).
 * Mapped to 429 by [rosemary_and_thyme.backend.controllers.GlobalExceptionHandler.handleTooManyRequests].
 * The coarser, IP-keyed limit in [rosemary_and_thyme.backend.filters.RateLimitFilter]
 * rejects requests directly at the filter level instead of throwing this.
 */
class TooManyRequestsException(message: String) : RuntimeException(message)
