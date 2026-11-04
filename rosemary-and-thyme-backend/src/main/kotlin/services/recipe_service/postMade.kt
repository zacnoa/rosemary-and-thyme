package rosemary_and_thyme.services.recipe_service

import rosemary_and_thyme.jooq.public.tables.references.RECIPES
import rosemary_and_thyme.models.exceptions.ResourceNotFoundException
import rosemary_and_thyme.services.RecipeService
import rosemary_and_thyme.services.Service
import java.util.UUID

/**
 * Atomically increments the number of times a recipe has been made.
 *
 * @param recipeId identifier of the recipe
 * @throws ResourceNotFoundException if the recipe does not exist
 */
fun RecipeService.postMade(recipeId: UUID) {
    Service.dsl.update(RECIPES)
        .set(RECIPES.TIMES_MADE, RECIPES.TIMES_MADE.plus(1))
        .where(RECIPES.ID.eq(recipeId))
        .execute()
        .also { affectedRows ->
            if (affectedRows != 1) {
                throw ResourceNotFoundException("Recipe with that id does not exist")
            }
        }
}
