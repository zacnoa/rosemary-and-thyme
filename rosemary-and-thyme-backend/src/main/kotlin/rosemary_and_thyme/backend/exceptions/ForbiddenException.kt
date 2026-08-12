package rosemary_and_thyme.backend.exceptions

/**
 * Thrown when an authenticated caller is not authorized for the resource they
 * requested - e.g. [rosemary_and_thyme.backend.services.recipes.RecipeService.saveRecipe]
 * when the caller isn't the recipe's owner. Mapped to 403 by
 * [rosemary_and_thyme.backend.controllers.GlobalExceptionHandler.handleForbiddenException].
 */
class ForbiddenException(message: String) : RuntimeException(message)
