package rosemaryandthyme.backend.exceptions

/** The recipe-vision service rejected the image or could not extract a recipe. */
class RecipeVisionException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/** The recipe-vision service could not be reached. */
class RecipeVisionUnavailableException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/** The recipe-vision service did not finish within the configured timeout. */
class RecipeVisionTimeoutException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
