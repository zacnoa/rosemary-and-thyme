package rosemary_and_thyme.services.recipe_service

import org.jooq.impl.DSL
import rosemary_and_thyme.jooq.public.tables.references.RECIPES
import rosemary_and_thyme.models.exceptions.ResourceNotFoundException
import rosemary_and_thyme.models.user.UserPrincipal
import rosemary_and_thyme.services.RecipeService
import rosemary_and_thyme.services.Service.dsl
import java.util.UUID

/**
 * Atomically toggles the private flag of a recipe owned by the authenticated user.
 *
 * @param recipeId identifier of the recipe to update
 * @param userPrincipal authenticated user who must own the recipe
 * @throws ResourceNotFoundException if the recipe does not exist or is owned by another user
 */
fun RecipeService.toggleRecipePrivate(recipeId: UUID, userPrincipal: UserPrincipal) {
    dsl.update(RECIPES)
        .set(RECIPES.IS_PRIVATE, DSL.not(RECIPES.IS_PRIVATE))
        .where(RECIPES.ID.eq(recipeId))
        .and(RECIPES.USER_ID.eq(userPrincipal.id))
        .execute()
        .also { affectedRows ->
            if (affectedRows != 1) {
                throw ResourceNotFoundException(
                    "Recipe does not exist or does not belong to the authenticated user"
                )
            }
        }
}
