package rosemary_and_thyme.backend.exceptions

/** Indicates that an authentication flow requires a verified email but did not. */
class EmailNotVerifiedException(message: String) : RuntimeException(message)
