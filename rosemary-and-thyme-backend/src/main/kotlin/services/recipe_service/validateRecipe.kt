package rosemary_and_thyme.services.recipe_service

import rosemary_and_thyme.models.responses.IngredientDTO
import rosemary_and_thyme.models.responses.InstructionDTO
import rosemary_and_thyme.models.requests.PostRecipeRequest
import rosemary_and_thyme.models.requests.PutRecipeRequest
import rosemary_and_thyme.services.RecipeService
import java.util.UUID

/**
 * Validates recipe data received by the create workflow.
 *
 * @param request recipe data to validate
 * @return validation messages, or an empty list when the recipe is valid
 */
fun RecipeService.validateRecipe(request: PostRecipeRequest): List<String> =
    validateRecipeData(
        title = request.title,
        description = request.description,
        difficulty = request.difficulty,
        cookTime = request.cookTime,
        imageIds = request.imageIds,
        ingredients = request.ingredients,
        instructions = request.instructions,
        ingredientsOrder = request.ingredientsOrder,
        instructionsOrder = request.instructionsOrder,
        heroImagesOrder = request.heroImagesOrder
    )

/**
 * Validates recipe data received by the update workflow.
 *
 * @param request replacement recipe data to validate
 * @return validation messages, or an empty list when the request is valid
 */
fun RecipeService.validateRecipe(request: PutRecipeRequest): List<String> {
    val messages = validateRecipeData(
        title = request.title,
        description = request.description,
        difficulty = request.difficulty,
        cookTime = request.cookTime,
        imageIds = request.imageIds,
        ingredients = request.ingredients,
        instructions = request.instructions,
        ingredientsOrder = request.ingredientsOrder,
        instructionsOrder = request.instructionsOrder,
        heroImagesOrder = request.heroImagesOrder
    )

    return messages
}

/**
 * Applies validation rules shared by recipe create and update operations.
 *
 * @param title recipe title
 * @param description recipe description
 * @param difficulty difficulty value to validate
 * @param cookTime cooking-duration text
 * @param imageIds identifiers of images associated with the recipe
 * @param ingredients ingredients keyed by their identifiers
 * @param instructions instructions keyed by their identifiers
 * @param ingredientsOrder ordered ingredient identifiers
 * @param instructionsOrder ordered instruction identifiers
 * @param heroImagesOrder ordered identifiers of images displayed as recipe heroes
 * @return all validation messages produced for the supplied data
 */
private fun validateRecipeData(
    title: String,
    description: String,
    difficulty: Short,
    cookTime: String,
    imageIds: Set<String>,
    ingredients: Map<String, IngredientDTO>,
    instructions: Map<String, InstructionDTO>,
    ingredientsOrder: List<String>,
    instructionsOrder: List<String>,
    heroImagesOrder: List<String>
): List<String> {
    val messages = mutableListOf<String>()
    val instructionImageIds = instructions.values.flatMap { instruction -> instruction.images }

    if (title.isEmpty()) {
        messages.add("Title should not be empty")
    }
    if (title.length > 60) {
        messages.add("title should not be longer than 60 characters")
    }
    if (imageIds.size > 20) {
        messages.add("Only 20 images are allowed per recipe")
    }
    val allImageIds = buildSet {
        addAll(imageIds)
        addAll(heroImagesOrder)
        addAll(instructionImageIds)
    }
    if (allImageIds.containsInvalidUuid()) {
        messages.add("Every image id must be a valid UUID")
    }
    val allIngredientIds = buildSet {
        addAll(ingredients.keys)
        ingredients.values.mapTo(this) { ingredient -> ingredient.id }
        addAll(ingredientsOrder)
    }
    if (allIngredientIds.containsInvalidUuid()) {
        messages.add("Every ingredient id must be a valid UUID")
    }
    val allInstructionIds = buildSet {
        addAll(instructions.keys)
        instructions.values.mapTo(this) { instruction -> instruction.id }
        addAll(instructionsOrder)
    }
    if (allInstructionIds.containsInvalidUuid()) {
        messages.add("Every instruction id must be a valid UUID")
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
    if (cookTime.length > 20) {
        messages.add("The cook time should not be longer than 20 characters")
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

    if (heroImagesOrder.size != heroImagesOrder.toSet().size) {
        messages.add("heroImagesOrder must not contain duplicate image ids")
    }

    if (!imageIds.containsAll(heroImagesOrder)) {
        messages.add("heroImagesOrder may reference only images belonging to the recipe")
    }

    if (!imageIds.containsAll(instructionImageIds)) {
        messages.add("Instructions may reference only images belonging to the recipe")
    }

    return messages
}

private fun Iterable<String>.containsInvalidUuid(): Boolean =
    any { id -> runCatching { UUID.fromString(id) }.isFailure }
