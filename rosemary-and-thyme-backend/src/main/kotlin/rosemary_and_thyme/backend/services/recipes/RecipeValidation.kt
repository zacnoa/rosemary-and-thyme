package rosemary_and_thyme.backend.services.recipes

import rosemary_and_thyme.backend.exceptions.RecipeValidationException
import rosemary_and_thyme.backend.models.recipes.RecipeWriteDTO

/**
 * Server-side mirror of the frontend's client-side save checks.
 */
object RecipeValidation {

    const val MAX_INGREDIENTS = 50
    const val MAX_INSTRUCTIONS = 50
    const val MAX_DESCRIPTION_WORDS = 1000
    const val MAX_NAME_CHARS = 100
    const val MAX_IMAGES = 20

    /**
 * Whitespace-delimited word count, ignoring leading/trailing/duplicate whitespace.
 */
    private fun wordCount(text: String): Int {
        val trimmed = text.trim()
        return if (trimmed.isEmpty()) 0 else trimmed.split(Regex("\\s+")).size
    }

    /**
 * Checks [recipe] against every save limit and returns the human-readable reasons.
 * @param recipe the recipe as submitted by the client.
 * @return blocking reasons, in check order.
 */
    fun getBlockers(recipe: RecipeWriteDTO): List<String> {
        val blockers = mutableListOf<String>()

        if (recipe.ingredientsOrder.size > MAX_INGREDIENTS) {
            blockers += "Too many ingredients (max $MAX_INGREDIENTS)"
        }
        if (recipe.instructionsOrder.size > MAX_INSTRUCTIONS) {
            blockers += "Too many steps (max $MAX_INSTRUCTIONS)"
        }
        if (wordCount(recipe.description) > MAX_DESCRIPTION_WORDS) {
            blockers += "Description too long (max $MAX_DESCRIPTION_WORDS words)"
        }
        if (recipe.name.length > MAX_NAME_CHARS) {
            blockers += "Title too long (max $MAX_NAME_CHARS characters)"
        }
        if (recipe.images.size > MAX_IMAGES) blockers += "Too many images (max $MAX_IMAGES)"
        if (recipe.ingredients.keys.size != recipe.ingredientsOrder.size || recipe.ingredients.keys != recipe.ingredientsOrder.toSet()) {
            blockers += "Ingredients and their order must contain the same unique ids"
        }
        if (recipe.instructions.keys.size != recipe.instructionsOrder.size || recipe.instructions.keys != recipe.instructionsOrder.toSet()) {
            blockers += "Instructions and their order must contain the same unique ids"
        }
        if (recipe.images.any { (id, image) -> id != image.id }) blockers += "Image map keys must match image ids"
        if (recipe.heroImagesOrder.size != recipe.heroImagesOrder.toSet().size || !recipe.heroImagesOrder.all { it in recipe.images }) {
            blockers += "Hero images must contain unique ids from the image collection"
        }
        val instructionImageIds = recipe.instructions.values.flatMap { it.images }
        if (instructionImageIds.size != instructionImageIds.toSet().size || !instructionImageIds.all { it in recipe.images }) {
            blockers += "Instruction images must contain unique ids from the image collection"
        }
        if ((recipe.heroImagesOrder + instructionImageIds).size != (recipe.heroImagesOrder + instructionImageIds).toSet().size) {
            blockers += "An image can belong to only one gallery"
        }

        return blockers
    }

    /**
 * Validates [recipe], throwing on the first violated limit. Called at the very.
 * @param recipe the recipe to validate.
 * @throws RecipeValidationException with [getBlockers]'s first reason, if any.
 */
    fun validate(recipe: RecipeWriteDTO) {
        val blockers = getBlockers(recipe)
        if (blockers.isNotEmpty()) {
            throw RecipeValidationException(blockers.first())
        }
    }
}
