package rosemary_and_thyme.services.recipe_service

import org.jooq.JSONB
import rosemary_and_thyme.jooq.public.tables.references.RECIPES
import rosemary_and_thyme.models.exceptions.BadRecipeFormat
import rosemary_and_thyme.models.exceptions.RecipeImageUploadException
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
 *
 * @param request validated replacement data
 * @param userPrincipal authenticated user who must own the recipe
 * @param recipeId identifier of the recipe to update
 * @throws BadRecipeFormat if the replacement data fails validation
 * @throws RecipeImageUploadException if one or more requested images are not available in R2
 * @throws ResourceNotFoundException if the recipe does not exist or is owned by another user
 * @throws IllegalStateException if R2 reports a failure while deleting removed images
 */


// The path parameter identifies the recipe being updated, while the ownership
// condition ensures that the authenticated user can modify only their own recipes.
// An authenticated user is authorized to update any recipe they own, so selecting
// another owned recipe ID is intentional behavior rather than an authorization bypass.
// Za običan edit endpoint korisnik uvijek može namjerno poslati ID nekog svojeg recepta.
// Backend ne može znati koji je recept korisnik “mislio” uređivati.
// Za zaštitu od slučajnog prepisivanja možemo kasnije dodati optimistic locking preko version stupca ili HTTP ETag/If-Match.

suspend fun RecipeService.putRecipe(
    request: PutRecipeRequest,
    userPrincipal: UserPrincipal,
    recipeId: UUID
) {

    val errorMessages = validateRecipe(request)
    if (errorMessages.isNotEmpty()) {
        throw BadRecipeFormat(message = errorMessages.joinToString(separator = "\n"))
    }

    val previousContentJson = Service.dsl.select(RECIPES.CONTENT)
        .from(RECIPES)
        .where(RECIPES.ID.eq(recipeId))
        .and(RECIPES.USER_ID.eq(userPrincipal.id))
        .fetchOne(RECIPES.CONTENT)
        ?: throw ResourceNotFoundException(
            "Recipe does not exist or does not belong to the authenticated user"
        )

    val previousContent = deserializeRecipeContent(previousContentJson.toString())
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

    val affectedRows = Service.dsl.update(RECIPES)
        .set(RECIPES.TITLE, request.title)
        .set(RECIPES.DESCRIPTION, request.description)
        .set(RECIPES.SIDE_NOTES, request.sideNote)
        .set(RECIPES.DIFFICULTY, request.difficulty)
        .set(RECIPES.PORTIONS, request.portions)
        .set(RECIPES.IS_PRIVATE, request.isPrivate)
        .set(RECIPES.CONTENT, contentJson)
        .where(RECIPES.ID.eq(recipeId))
        .and(RECIPES.USER_ID.eq(userPrincipal.id))
        .execute()

    if (affectedRows != 1) {
        throw ResourceNotFoundException(
            "Recipe does not exist or does not belong to the authenticated user"
        )
    }

    Service.s3Service.deleteImages(removedImageObjectKeys)
}
