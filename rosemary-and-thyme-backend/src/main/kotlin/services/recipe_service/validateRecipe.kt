package rosemary_and_thyme.services.recipe_service

import rosemary_and_thyme.models.recipe.RecipeDTO
import rosemary_and_thyme.models.recipe.IngredientDTO
import rosemary_and_thyme.models.recipe.InstructionDTO
import rosemary_and_thyme.models.recipe.RecipeImageDTO
import rosemary_and_thyme.models.requests.UpdateRecipeRequest
import rosemary_and_thyme.services.RecipeService

/**
 * Validates recipe data received by the create workflow.
 *
 * @param recipeDTO recipe data to validate
 * @return validation messages, or an empty list when the recipe is valid
 */
fun RecipeService.validateRecipe(recipeDTO: RecipeDTO): List<String> =
    validateRecipeData(
        title = recipeDTO.title,
        description = recipeDTO.description,
        difficulty = recipeDTO.difficulty,
        images = recipeDTO.images,
        ingredients = recipeDTO.ingredients,
        instructions = recipeDTO.instructions,
        ingredientsOrder = recipeDTO.ingredientsOrder,
        instructionsOrder = recipeDTO.instructionsOrder
    )

/**
 * Validates recipe data received by the update workflow.
 *
 * @param request replacement recipe data to validate
 * @return validation messages, or an empty list when the request is valid
 */
fun RecipeService.validateRecipe(request: UpdateRecipeRequest): List<String> =
    validateRecipeData(
        title = request.title,
        description = request.description,
        difficulty = request.difficulty,
        images = request.images,
        ingredients = request.ingredients,
        instructions = request.instructions,
        ingredientsOrder = request.ingredientsOrder,
        instructionsOrder = request.instructionsOrder
    )

/**
 * Applies validation rules shared by recipe create and update operations.
 *
 * @param title recipe title
 * @param description recipe description
 * @param difficulty difficulty value to validate
 * @param images images associated with the recipe
 * @param ingredients ingredients keyed by their identifiers
 * @param instructions instructions keyed by their identifiers
 * @param ingredientsOrder ordered ingredient identifiers
 * @param instructionsOrder ordered instruction identifiers
 * @return all validation messages produced for the supplied data
 */
private fun validateRecipeData(
    title: String,
    description: String,
    difficulty: Short,
    images: Map<String, RecipeImageDTO>,
    ingredients: Map<String, IngredientDTO>,
    instructions: Map<String, InstructionDTO>,
    ingredientsOrder: List<String>,
    instructionsOrder: List<String>
): List<String> {
    val messages = mutableListOf<String>()

    if (title.isEmpty()) {
        messages.add("Title should not be empty")
    }
    if (title.length > 60) {
        messages.add("title should not be longer than 60 characters")
    }
    if (images.keys.size > 20) {
        messages.add("Only 20 images are allowed per recipe")
    }
    if (ingredients.values.any { it.name.length > 40 }) {
        messages.add("ingredient name should not be longer than 40 characters")
    }
    if (instructions.values.any { it.text.length > 1000 }) {
        messages.add("No instruction should be longer than a 1000 characters")
    }
    if (difficulty !in 1..5) {
        messages.add("The difficulty should be between 1 and 5")
    }
    if (description.length > 1000) {
        messages.add("The description should not be longer than 1000 characters")
    }

    val instructionIds = instructions.keys
    val instructionOrderIsValid =
        instructionsOrder.size == instructionIds.size &&
                instructionsOrder.toSet() == instructionIds

    if (!instructionOrderIsValid) {
        messages.add("instructionsOrder must contain every instruction id exactly once")
    }

    if (instructions.any { (id, instruction) -> id != instruction.id }) {
        messages.add("Every instruction map key must match the instruction id")
    }

    val ingredientIds = ingredients.keys
    val ingredientOrderIsValid =
        ingredientsOrder.size == ingredientIds.size &&
                ingredientsOrder.toSet() == ingredientIds

    if (!ingredientOrderIsValid) {
        messages.add("ingredientsOrder must contain every ingredient id exactly once")
    }

    if (ingredients.any { (id, ingredient) -> id != ingredient.id }) {
        messages.add("Every ingredient map key must match the ingredient id")
    }

    return messages
}
