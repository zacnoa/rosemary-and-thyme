package rosemaryandthyme.backend.exceptions

/** Thrown when a caller exceeds one of the per-identity limits enforced via [rosemaryandthyme.backend.services.ratelimit.RateLimiterService] - e.g. */
class TooManyRequestsException(message: String) : RuntimeException(message)
