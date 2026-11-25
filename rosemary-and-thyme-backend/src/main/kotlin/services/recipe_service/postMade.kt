package rosemary_and_thyme.services.recipe_service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rosemary_and_thyme.jooq.public.tables.references.RECIPES
import rosemary_and_thyme.models.exceptions.ResourceNotFoundException
import rosemary_and_thyme.models.user.UserPrincipal
import rosemary_and_thyme.services.RecipeService
import rosemary_and_thyme.services.Service
import java.util.UUID

/**
 * Atomically increments the made counter for a completed recipe visible to the authenticated user.
 *
 * @param recipeId identifier of the recipe
 * @param userPrincipal authenticated viewer, who must own a private recipe
 * @throws ResourceNotFoundException if the recipe is missing, a draft, or private and owned by someone else
 */
suspend fun RecipeService.postMade(recipeId: UUID, userPrincipal: UserPrincipal) {
    withContext(Dispatchers.IO) {
        Service.dsl.update(RECIPES)
            .set(RECIPES.TIMES_MADE, RECIPES.TIMES_MADE.plus(1))
            .where(RECIPES.ID.eq(recipeId))
            .and(RECIPES.IS_DRAFT.eq(false))
            .and(
                RECIPES.IS_PRIVATE.eq(false)
                    .or(RECIPES.USER_ID.eq(userPrincipal.id))
            )
            .execute()
            .also { affectedRows ->
                if (affectedRows != 1) {
                    throw ResourceNotFoundException("Recipe with that id does not exist")
                }
            }
    }
}
