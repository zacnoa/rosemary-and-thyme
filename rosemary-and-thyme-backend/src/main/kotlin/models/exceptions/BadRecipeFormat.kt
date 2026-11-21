package rosemary_and_thyme.models.exceptions

/** Indicates that submitted recipe content violates one or more validation rules. */
class BadRecipeFormat(message: String) : RuntimeException(message)
