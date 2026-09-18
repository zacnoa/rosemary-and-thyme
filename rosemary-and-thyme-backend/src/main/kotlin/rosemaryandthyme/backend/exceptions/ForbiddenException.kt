package rosemaryandthyme.backend.exceptions

/** Thrown when an authenticated caller is not authorized for the resource they requested - e.g. */
class ForbiddenException(message: String) : RuntimeException(message)
