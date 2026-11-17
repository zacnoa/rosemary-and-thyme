package rosemary_and_thyme.services.recipe_service

import rosemary_and_thyme.jooq.public.tables.references.RECIPES
import rosemary_and_thyme.models.exceptions.ResourceNotFoundException
import rosemary_and_thyme.models.user.UserPrincipal
import rosemary_and_thyme.services.RecipeService
import rosemary_and_thyme.services.Service
import rosemary_and_thyme.services.s3_service.deleteImages
import rosemary_and_thyme.services.s3_service.recipeImageObjectKey
import java.util.UUID

/**
 * Deletes a recipe owned by the authenticated user, then deletes its referenced image objects.
 * R2 failures can leave orphan objects for a later cleanup job.
 *
 * @param recipeId identifier of the recipe to delete
 * @param userPrincipal authenticated user who must own the recipe
 * @throws ResourceNotFoundException if the recipe does not exist or is owned by another user
 * @throws IllegalStateException if R2 reports an image deletion failure after the recipe was deleted
 */
suspend fun RecipeService.deleteRecipe(recipeId: UUID, userPrincipal: UserPrincipal) {
    val content = Service.dsl.deleteFrom(RECIPES)
        .where(RECIPES.ID.eq(recipeId))
        .and(RECIPES.USER_ID.eq(userPrincipal.id))
        .returning(RECIPES.CONTENT)
        .fetchOne(RECIPES.CONTENT)
        ?: throw ResourceNotFoundException(
            "Recipe does not exist or does not belong to the authenticated user"
        )

    val objectKeys = deserializeRecipeContent(content.data()).imageIds
        .mapTo(mutableSetOf()) { imageId ->
            Service.s3Service.recipeImageObjectKey(
                userId = userPrincipal.id.toString(),
                recipeId = recipeId.toString(),
                imageId = imageId
            )
        }

    Service.s3Service.deleteImages(objectKeys)
}
