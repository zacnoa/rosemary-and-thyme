package rosemary_and_thyme.models.exceptions

/** Indicates that uploaded recipe images are incomplete or violate upload constraints. */
class RecipeImageUploadException(message:String): RuntimeException(message)
