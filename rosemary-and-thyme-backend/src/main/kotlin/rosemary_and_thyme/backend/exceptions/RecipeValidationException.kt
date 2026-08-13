package rosemary_and_thyme.backend.exceptions

/**
 * Thrown by [rosemary_and_thyme.backend.services.recipes.RecipeValidation.validate]
 * when a submitted [rosemary_and_thyme.backend.models.recipes.RecipeDTO] violates one
 * of the save limits enforced there (too many ingredients/steps, description/title too
 * long). Caught by
 * [rosemary_and_thyme.backend.controllers.GlobalExceptionHandler.handleRecipeValidation]
 * and turned into a 400 problem-detail response, whose `detail` is exactly the message
 * passed here - the same wording [RecipeService.saveRecipe][rosemary_and_thyme.backend.services.recipes.RecipeService.saveRecipe]
 * refuses to save.
 */
class RecipeValidationException(message: String) : RuntimeException(message)
