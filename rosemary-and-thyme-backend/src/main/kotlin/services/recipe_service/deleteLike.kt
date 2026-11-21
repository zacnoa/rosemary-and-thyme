package rosemary_and_thyme.services.recipe_service

import rosemary_and_thyme.jooq.public.tables.references.RECIPE_LIKES
import rosemary_and_thyme.models.exceptions.ResourceNotFoundException
import rosemary_and_thyme.models.user.UserPrincipal
import rosemary_and_thyme.services.RecipeService
import rosemary_and_thyme.services.Service
import java.util.UUID

/**
 * Removes the authenticated user's like from a recipe.
 *
 * @param recipeId identifier of the recipe to unlike
 * @param userPrincipal authenticated owner of the like
 * @throws ResourceNotFoundException if the user has not liked the recipe
 */
fun RecipeService.deleteLike(recipeId: UUID, userPrincipal: UserPrincipal) {

    Service.dsl.deleteFrom(RECIPE_LIKES)
        .where(RECIPE_LIKES.RECIPE_ID.eq(recipeId))
        .and(RECIPE_LIKES.USER_ID.eq(userPrincipal.id))
        .execute()
        .also { affectedRows ->
            if (affectedRows != 1) {
                throw ResourceNotFoundException(
                    "The authenticated user has not liked this recipe"
                )
            }
        }

}
