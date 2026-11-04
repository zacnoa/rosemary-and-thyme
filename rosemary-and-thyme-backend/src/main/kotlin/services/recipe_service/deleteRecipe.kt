package rosemary_and_thyme.services.recipe_service

import rosemary_and_thyme.jooq.public.tables.references.RECIPES
import rosemary_and_thyme.models.exceptions.ResourceNotFoundException
import rosemary_and_thyme.models.user.UserPrincipal
import rosemary_and_thyme.services.RecipeService
import rosemary_and_thyme.services.Service
import java.util.UUID

/**
 * Deletes a recipe owned by the authenticated user.
 *
 * @param recipeId identifier of the recipe to delete
 * @param userPrincipal authenticated user who must own the recipe
 * @throws ResourceNotFoundException if the recipe does not exist or is owned by another user
 */
fun RecipeService.deleteRecipe(recipeId: UUID, userPrincipal: UserPrincipal) {
    Service.dsl.deleteFrom(RECIPES)
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
