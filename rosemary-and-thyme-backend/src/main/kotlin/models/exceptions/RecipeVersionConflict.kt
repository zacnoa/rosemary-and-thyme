package rosemary_and_thyme.models.exceptions

/** Indicates that a recipe replacement no longer matches the stored edit version. */
class RecipeVersionConflict(message: String) : RuntimeException(message)
