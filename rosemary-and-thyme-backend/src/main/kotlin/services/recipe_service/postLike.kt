package rosemary_and_thyme.services.recipe_service

import io.ktor.server.plugins.BadRequestException
import org.jooq.impl.DSL
import rosemary_and_thyme.jooq.public.tables.references.RECIPE_LIKES
import rosemary_and_thyme.jooq.public.tables.references.RECIPES
import rosemary_and_thyme.models.user.UserPrincipal
import rosemary_and_thyme.services.RecipeService
import rosemary_and_thyme.services.Service
import java.time.OffsetDateTime
import java.util.UUID

/**
 * Creates a like for a recipe on behalf of the authenticated user.
 * Recipe owners cannot like their own recipes, and duplicate likes are ignored by the database.
 *
 * @param recipeId identifier of the recipe to like
 * @param userPrincipal authenticated user creating the like
 * @throws BadRequestException if the recipe is unavailable, already liked, or owned by the user
 */
fun RecipeService.postLike(
    recipeId: UUID,
    userPrincipal: UserPrincipal
) {
    val affectedRows = Service.dsl.insertInto(
        RECIPE_LIKES,
        RECIPE_LIKES.RECIPE_ID,
        RECIPE_LIKES.USER_ID,
        RECIPE_LIKES.CREATED_AT
    )
        .select(
            Service.dsl.select(
                RECIPES.ID,
                DSL.value(userPrincipal.id),
                DSL.value(OffsetDateTime.now())
            )
                .from(RECIPES)
                .where(RECIPES.ID.eq(recipeId))
                .and(RECIPES.USER_ID.ne(userPrincipal.id))
        )
        .onConflictDoNothing()
        .execute()

    if (affectedRows != 1) {
        throw BadRequestException(
            "The recipe does not exist, is already liked, or belongs to the authenticated user"
        )
    }
}
