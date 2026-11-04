package rosemary_and_thyme.services.recipe_service

import aws.sdk.kotlin.services.s3.listObjectsV2
import rosemary_and_thyme.jooq.public.tables.references.RECIPES
import rosemary_and_thyme.models.exceptions.RecipeImageUploadException
import rosemary_and_thyme.models.exceptions.ResourceNotFoundException
import rosemary_and_thyme.models.user.UserPrincipal
import rosemary_and_thyme.services.RecipeService
import rosemary_and_thyme.services.Service
import rosemary_and_thyme.services.s3_service.recipeImageKey
import rosemary_and_thyme.services.s3_service.recipeImagePrefix
import java.util.UUID

private const val MAX_IMAGE_SIZE_BYTES = 10L * 1024 * 1024

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

    val recipeRecord = Service.dsl.select(RECIPES.CONTENT, RECIPES.IS_DRAFT)
        .from(RECIPES)
        .where(RECIPES.ID.eq(recipeId))
        .and(RECIPES.USER_ID.eq(userPrincipal.id))
        .fetchOne()
        ?: throw ResourceNotFoundException("Recipe draft with that id does not exist")

    if (recipeRecord.get(RECIPES.IS_DRAFT) != true) return

    val imageIds = deserializeRecipeContent(
        recipeRecord.get(RECIPES.CONTENT).toString()
    ).imageIds

    val expectedKeys = imageIds.map { id ->
        Service.s3Service.recipeImageKey(
            userId = userPrincipal.id.toString(),
            recipeId = recipeId.toString(),
            imageId = id
        )
    }.toSet()

    val imagePrefix = Service.s3Service.recipeImagePrefix(
        userId = userPrincipal.id.toString(),
        recipeId = recipeId.toString()
    )

    val uploadedObjects = Service.s3Client.listObjectsV2 {
        bucket = Service.s3Bucket
        prefix = imagePrefix
        maxKeys = expectedKeys.size + 1
    }.contents.orEmpty()

    val uploadedKeys = uploadedObjects.mapNotNull { it.key }.toSet()

    if (uploadedKeys != expectedKeys) {
        throw RecipeImageUploadException("Uploaded recipe images do not match the expected images")
    }

    if (uploadedObjects.any { uploadedObject ->
            val size = uploadedObject.size ?: 0L
            size !in 1..MAX_IMAGE_SIZE_BYTES
        }
    ) {
        throw RecipeImageUploadException("One or more recipe images have an invalid size")
    }

    Service.dsl.update(RECIPES)
        .set(RECIPES.IS_DRAFT, false)
        .where(RECIPES.ID.eq(recipeId))
        .and(RECIPES.USER_ID.eq(userPrincipal.id))
        .returning(RECIPES.ID)
        .fetchOne(RECIPES.ID)
        ?: throw ResourceNotFoundException("Recipe draft with that id does not exist")
}
