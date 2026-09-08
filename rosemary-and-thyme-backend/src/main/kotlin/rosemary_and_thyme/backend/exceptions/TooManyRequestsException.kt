package rosemary_and_thyme.backend.exceptions

/** Thrown when a caller exceeds one of the per-identity limits enforced via [rosemary_and_thyme.backend.services.ratelimit.RateLimiterService] - e.g. */
class TooManyRequestsException(message: String) : RuntimeException(message)
