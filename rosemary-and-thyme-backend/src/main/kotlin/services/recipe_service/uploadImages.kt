package rosemary_and_thyme.services.recipe_service

import aws.sdk.kotlin.services.s3.listObjectsV2
import io.ktor.server.plugins.BadRequestException
import rosemary_and_thyme.jooq.public.tables.references.RECIPES
import rosemary_and_thyme.models.exceptions.ResourceNotFoundException
import rosemary_and_thyme.models.responses.ImageUploadResponse
import rosemary_and_thyme.models.user.UserPrincipal
import rosemary_and_thyme.services.RecipeService
import rosemary_and_thyme.services.Service
import rosemary_and_thyme.services.s3_service.PresignedOperation
import rosemary_and_thyme.services.s3_service.getPresignedUrl
import rosemary_and_thyme.services.s3_service.recipeImageKey
import rosemary_and_thyme.services.s3_service.recipeImagePrefix
import java.util.UUID

private const val MAX_RECIPE_IMAGES = 20

/**
 * Creates direct-upload grants for images that do not already exist in R2.
 * The supplied image IDs represent the complete image set intended for the recipe.
 *
 * @param recipeId recipe whose images are being prepared
 * @param userPrincipal authenticated owner of the recipe
 * @param imageIds complete set of client-generated image identifiers
 * @return presigned PUT grants for image identifiers whose R2 objects do not exist
 * @throws BadRequestException if the IDs are invalid or do not match a draft's declared images
 * @throws ResourceNotFoundException if the recipe does not exist or belongs to another user
 */
suspend fun RecipeService.uploadImages(
    recipeId: UUID,
    userPrincipal: UserPrincipal,
    imageIds: Set<String>
): Set<ImageUploadResponse> {
    if (imageIds.size > MAX_RECIPE_IMAGES) {
        throw BadRequestException("Only $MAX_RECIPE_IMAGES images are allowed per recipe")
    }

    if (imageIds.any { imageId -> runCatching { UUID.fromString(imageId) }.isFailure }) {
        throw BadRequestException("Every image id must be a valid UUID")
    }

    val recipeRecord = Service.dsl.select(RECIPES.CONTENT, RECIPES.IS_DRAFT)
        .from(RECIPES)
        .where(RECIPES.ID.eq(recipeId))
        .and(RECIPES.USER_ID.eq(userPrincipal.id))
        .fetchOne()
        ?: throw ResourceNotFoundException("Recipe with that id does not exist")

    if (recipeRecord.get(RECIPES.IS_DRAFT) == true) {
        val declaredImageIds = deserializeRecipeContent(
            recipeRecord.get(RECIPES.CONTENT).toString()
        ).imageIds

        if (imageIds != declaredImageIds) {
            throw BadRequestException(
                "Image ids must match the images declared by the recipe draft"
            )
        }
    }

    if (imageIds.isEmpty()) return emptySet()

    val imagePrefix = Service.s3Service.recipeImagePrefix(
        userId = userPrincipal.id.toString(),
        recipeId = recipeId.toString()
    )

    val existingObjectKeys = Service.s3Client.listObjectsV2 {
        bucket = Service.s3Bucket
        prefix = imagePrefix
        maxKeys = 1000
    }.contents.orEmpty()
        .mapNotNull { uploadedObject -> uploadedObject.key }
        .toSet()

    val missingImageIds = imageIds.filterTo(mutableSetOf()) { imageId ->
        Service.s3Service.recipeImageKey(
            userId = userPrincipal.id.toString(),
            recipeId = recipeId.toString(),
            imageId = imageId
        ) !in existingObjectKeys
    }

    return missingImageIds.mapTo(mutableSetOf()) { imageId ->
        val objectKey = Service.s3Service.recipeImageKey(
            userId = userPrincipal.id.toString(),
            recipeId = recipeId.toString(),
            imageId = imageId
        )

        ImageUploadResponse(
            imageId = imageId,
            uploadUrl = Service.s3Service.getPresignedUrl(
                operation = PresignedOperation.PUT,
                objectKey = objectKey
            )
        )
    }
}
