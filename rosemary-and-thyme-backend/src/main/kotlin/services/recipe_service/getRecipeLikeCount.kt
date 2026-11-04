package rosemary_and_thyme.services.recipe_service

import rosemary_and_thyme.jooq.public.tables.references.RECIPE_LIKES
import rosemary_and_thyme.services.RecipeService
import rosemary_and_thyme.services.Service
import java.util.UUID

/**
 * Counts the likes associated with a recipe.
 *
 * @param recipeId identifier of the recipe
 * @return the total number of likes
 */
fun RecipeService.getRecipeLikeCount(recipeId: UUID): Int =
    Service.dsl.selectCount()
        .from(RECIPE_LIKES)
        .where(RECIPE_LIKES.RECIPE_ID.eq(recipeId))
        .fetchSingle()
        .value1()
