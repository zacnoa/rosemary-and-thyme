package rosemary_and_thyme.services.recipe_service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rosemary_and_thyme.jooq.public.tables.references.RECIPES
import rosemary_and_thyme.models.exceptions.RecipeImageUploadException
import rosemary_and_thyme.models.exceptions.ResourceNotFoundException
import rosemary_and_thyme.models.user.UserPrincipal
import rosemary_and_thyme.services.RecipeService
import rosemary_and_thyme.services.Service
import rosemary_and_thyme.services.s3_service.verifyUploadedRecipeImages
import java.util.UUID

/**
 * Verifies that every expected recipe image was uploaded and publishes the draft.
 * Repeating the operation for an already completed recipe succeeds without performing another R2 lookup.
 *
 * @param recipeId identifier of the draft to complete
 * @param userPrincipal authenticated owner of the draft
 * @throws ResourceNotFoundException if the recipe does not exist or belongs to another user
 * @throws RecipeImageUploadException if uploaded objects do not match the expected images or have invalid sizes
 */
suspend fun RecipeService.completeRecipeDraft(recipeId: UUID, userPrincipal: UserPrincipal) {

    val recipeRecord = withContext(Dispatchers.IO) {
        Service.dsl.select(RECIPES.CONTENT, RECIPES.IS_DRAFT)
            .from(RECIPES)
            .where(RECIPES.ID.eq(recipeId))
            .and(RECIPES.USER_ID.eq(userPrincipal.id))
            .fetchOne()
            ?: throw ResourceNotFoundException("Recipe draft with that id does not exist")
    }

    if (recipeRecord.get(RECIPES.IS_DRAFT) != true) return

    val imageIds = deserializeRecipeContent(
        recipeRecord.get(RECIPES.CONTENT).toString()
    ).imageIds

    Service.s3Service.verifyUploadedRecipeImages(
        recipeId = recipeId,
        userId = userPrincipal.id,
        imageIds = imageIds,
        allowAdditionalObjects = false
    )

    withContext(Dispatchers.IO) {
        Service.dsl.update(RECIPES)
            .set(RECIPES.IS_DRAFT, false)
            .where(RECIPES.ID.eq(recipeId))
            .and(RECIPES.USER_ID.eq(userPrincipal.id))
            .returning(RECIPES.ID)
            .fetchOne(RECIPES.ID)
            ?: throw ResourceNotFoundException("Recipe draft with that id does not exist")
    }
}
