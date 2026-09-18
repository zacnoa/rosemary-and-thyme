package rosemaryandthyme.backend.exceptions

/** Thrown by [rosemaryandthyme.backend.services.recipes.RecipeValidation.validate] when a submitted [rosemaryandthyme.backend.models.recipes.RecipeDTO]… */
class RecipeValidationException(message: String) : RuntimeException(message)
