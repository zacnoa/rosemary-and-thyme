package rosemary_and_thyme.services.recipe_service

import org.jooq.JSONB
import rosemary_and_thyme.jooq.public.tables.references.RECIPES
import rosemary_and_thyme.models.exceptions.BadRecipeFormat
import rosemary_and_thyme.models.exceptions.RecipeImageUploadException
import rosemary_and_thyme.models.exceptions.RecipeVersionConflict
import rosemary_and_thyme.models.exceptions.ResourceNotFoundException
import rosemary_and_thyme.models.responses.RecipeContentDTO
import rosemary_and_thyme.models.requests.PutRecipeRequest
import rosemary_and_thyme.models.user.UserPrincipal
import rosemary_and_thyme.services.RecipeService
import rosemary_and_thyme.services.Service
import rosemary_and_thyme.services.s3_service.deleteImages
import rosemary_and_thyme.services.s3_service.recipeImageObjectKey
import rosemary_and_thyme.services.s3_service.verifyUploadedRecipeImages
import java.util.UUID

/**
 * Replaces the editable data of a recipe owned by the authenticated user.
 * Checks the edit version before reading R2 and again in the atomic update.
 * Removed images are deleted only after a successful versioned update.
 *
 * @param request validated replacement data
 * @param userPrincipal authenticated user who must own the recipe
 * @param recipeId identifier of the recipe to update
 * @return the new edit version of the saved recipe
 * @throws BadRecipeFormat if the replacement data fails validation
 * @throws RecipeImageUploadException if one or more requested images are not available in R2
 * @throws RecipeVersionConflict if the version is stale or the recipe disappears before the update
 * @throws ResourceNotFoundException if the recipe does not exist or is owned by another user
 * @throws IllegalStateException if R2 reports a failure while deleting removed images
 */


// The path parameter identifies the recipe being updated, while the ownership
// condition ensures that the authenticated user can modify only their own recipes.
// An authenticated user is authorized to update any recipe they own, so selecting
// another owned recipe ID is intentional behavior rather than an authorization bypass.
// Version checks reject stale replacements before they can delete recipe images.

suspend fun RecipeService.putRecipe(
    request: PutRecipeRequest,
    userPrincipal: UserPrincipal,
    recipeId: UUID
): Long {

    val errorMessages = validateRecipe(request)
    if (errorMessages.isNotEmpty()) {
        throw BadRecipeFormat(message = errorMessages.joinToString(separator = "\n"))
    }

    val previousRecord = Service.dsl.select(RECIPES.CONTENT, RECIPES.VERSION)
        .from(RECIPES)
        .where(RECIPES.ID.eq(recipeId))
        .and(RECIPES.USER_ID.eq(userPrincipal.id))
        .fetchOne()
        ?: throw ResourceNotFoundException(
            "Recipe does not exist or does not belong to the authenticated user"
        )

    if (previousRecord.get(RECIPES.VERSION) != request.version) {
        throw RecipeVersionConflict(
            "The recipe was modified. Reload it before saving again."
        )
    }

    val previousContent = deserializeRecipeContent(
        checkNotNull(previousRecord.get(RECIPES.CONTENT)).data()
    )
    val removedImageObjectKeys = (previousContent.imageIds - request.imageIds)
        .mapTo(mutableSetOf()) { imageId ->
            Service.s3Service.recipeImageObjectKey(
                userId = userPrincipal.id.toString(),
                recipeId = recipeId.toString(),
                imageId = imageId
            )
        }

    Service.s3Service.verifyUploadedRecipeImages(
        recipeId = recipeId,
        userId = userPrincipal.id,
        imageIds = request.imageIds,
        allowAdditionalObjects = true
    )

    val content = RecipeContentDTO(
        imageIds = request.imageIds,
        ingredients = request.ingredients,
        instructions = request.instructions,
        ingredientsOrder = request.ingredientsOrder,
        instructionsOrder = request.instructionsOrder,
        heroImagesOrder = request.heroImagesOrder
    )

    val contentJson = JSONB.jsonb(
        serializeRecipeContent(content)
    )

    val version = Service.dsl.update(RECIPES)
        .set(RECIPES.TITLE, request.title)
        .set(RECIPES.DESCRIPTION, request.description)
        .set(RECIPES.SIDE_NOTES, request.sideNote)
        .set(RECIPES.DIFFICULTY, request.difficulty)
        .set(RECIPES.PORTIONS, request.portions)
        .set(RECIPES.IS_PRIVATE, request.isPrivate)
        .set(RECIPES.CONTENT, contentJson)
        .set(RECIPES.VERSION, RECIPES.VERSION.plus(1L))
        .where(RECIPES.ID.eq(recipeId))
        .and(RECIPES.USER_ID.eq(userPrincipal.id))
        .and(RECIPES.VERSION.eq(request.version))
        .returning(RECIPES.VERSION)
        .fetchOne(RECIPES.VERSION)
        ?: throw RecipeVersionConflict(
            "The recipe changed or is no longer available. Reload it before saving again."
        )

    Service.s3Service.deleteImages(removedImageObjectKeys)

    return version
}
