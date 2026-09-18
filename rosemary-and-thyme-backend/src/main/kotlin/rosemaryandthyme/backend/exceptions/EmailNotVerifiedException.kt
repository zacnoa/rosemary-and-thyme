package rosemaryandthyme.backend.exceptions

/** Indicates that an authentication flow requires a verified email but did not. */
class EmailNotVerifiedException(message: String) : RuntimeException(message)
