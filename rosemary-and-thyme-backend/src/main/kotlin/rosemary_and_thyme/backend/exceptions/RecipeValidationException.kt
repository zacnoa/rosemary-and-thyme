package rosemary_and_thyme.backend.exceptions

/** Thrown by [rosemary_and_thyme.backend.services.recipes.RecipeValidation.validate] when a submitted [rosemary_and_thyme.backend.models.recipes.RecipeDTO]… */
class RecipeValidationException(message: String) : RuntimeException(message)
