package rosemary_and_thyme.models.exceptions

/** Indicates that valid login credentials belong to an account whose email is not verified. */
class EmailNotVerified(message: String) : RuntimeException(message)
