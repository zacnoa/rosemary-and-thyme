package rosemary_and_thyme.backend.services.recipes

import rosemary_and_thyme.backend.exceptions.RecipeValidationException
import rosemary_and_thyme.backend.models.recipes.RecipeDTO

/**
 * Server-side mirror of the frontend's client-side save checks
 * (`utils/validateRecipe.ts` on the frontend, used by RecipeProvider.saveRecipe and
 * the dock's slide-to-save panel). The frontend gate stops most invalid submissions
 * before a request is even sent, but that's a UX convenience only - nothing stops a
 * client from calling `PUT /recipe/{id}` directly with a recipe that violates these
 * limits, so the exact same checks are enforced again here, which is the real gate.
 * Keep the limits/messages here and on the frontend in sync if either changes.
 */
object RecipeValidation {

    const val MAX_INGREDIENTS = 50
    const val MAX_INSTRUCTIONS = 50
    const val MAX_DESCRIPTION_WORDS = 1000
    const val MAX_NAME_CHARS = 100

    /** Whitespace-delimited word count, ignoring leading/trailing/duplicate whitespace. */
    private fun wordCount(text: String): Int {
        val trimmed = text.trim()
        return if (trimmed.isEmpty()) 0 else trimmed.split(Regex("\\s+")).size
    }

    /**
     * Checks [recipe] against every save limit and returns the human-readable reasons
     * it violates one, in check order - an empty list means [recipe] is within every
     * limit.
     *
     * @param recipe the recipe as submitted by the client
     * @return blocking reasons, in check order
     */
    fun getBlockers(recipe: RecipeDTO): List<String> {
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

        return blockers
    }

    /**
     * Validates [recipe], throwing on the first violated limit. Called at the very
     * top of [RecipeService.saveRecipe], before any DB or Cloudinary work starts, so
     * an invalid submission fails fast with no side effects (no upload, no DB write)
     * rather than being caught partway through.
     *
     * @param recipe the recipe to validate
     * @throws RecipeValidationException with [getBlockers]'s first reason, if any
     */
    fun validate(recipe: RecipeDTO) {
        val blockers = getBlockers(recipe)
        if (blockers.isNotEmpty()) {
            throw RecipeValidationException(blockers.first())
        }
    }
}
