package rosemary_and_thyme.services.recipe_service

import aws.sdk.kotlin.services.s3.listObjectsV2
import io.ktor.server.plugins.BadRequestException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rosemary_and_thyme.jooq.public.tables.references.RECIPES
import rosemary_and_thyme.models.exceptions.ResourceNotFoundException
import rosemary_and_thyme.models.responses.ImageUploadResponse
import rosemary_and_thyme.models.user.UserPrincipal
import rosemary_and_thyme.services.RecipeService
import rosemary_and_thyme.services.Service
import rosemary_and_thyme.services.s3_service.PresignedOperation
import rosemary_and_thyme.services.s3_service.getPresignedUrl
import rosemary_and_thyme.services.s3_service.recipeImageObjectKey
import rosemary_and_thyme.services.s3_service.recipeImageObjectKeyPrefix
import java.util.UUID

private const val MAX_RECIPE_IMAGES = 20

/**
 * Creates direct-upload grants for images that do not already exist in R2.
 * The supplied image IDs represent the complete image set intended for the recipe.
 * The image limit applies to retained and newly added images after the requested deletions.
 *
 * @param recipeId recipe whose images are being prepared
 * @param userPrincipal authenticated owner of the recipe
 * @param imageIds complete set of client-generated image identifiers
 * @param imageSizes exact file sizes in bytes for images not yet in R2
 * @return presigned PUT grants for image identifiers whose R2 objects do not exist
 * @throws BadRequestException if the final image set exceeds the limit, the IDs are invalid,
 * do not match a draft's images, or an upload size is missing or invalid
 * @throws ResourceNotFoundException if the recipe does not exist or belongs to another user
 */
suspend fun RecipeService.uploadImages(
    recipeId: UUID,
    userPrincipal: UserPrincipal,
    imageIds: Set<String>,
    imageSizes: Map<String, Long>
): Set<ImageUploadResponse> {
    if (imageIds.any { imageId -> runCatching { UUID.fromString(imageId) }.isFailure }) {
        throw BadRequestException("Every image id must be a valid UUID")
    }

    val recipeRecord = withContext(Dispatchers.IO) {
        Service.dsl.select(RECIPES.CONTENT, RECIPES.IS_DRAFT)
            .from(RECIPES)
            .where(RECIPES.ID.eq(recipeId))
            .and(RECIPES.USER_ID.eq(userPrincipal.id))
            .fetchOne()
            ?: throw ResourceNotFoundException("Recipe with that id does not exist")
    }

    val existingImageIds = deserializeRecipeContent(
        recipeRecord.get(RECIPES.CONTENT).toString()
    ).imageIds

    if (recipeRecord.get(RECIPES.IS_DRAFT) == true) {
        if (imageIds != existingImageIds) {
            throw BadRequestException(
                "Image ids must match the images declared by the recipe draft"
            )
        }
    }

    val deletedImages = existingImageIds - imageIds
    val newImages = imageIds - existingImageIds
    val imagesAfterSave = (existingImageIds - deletedImages) + newImages

    if (imagesAfterSave.size > MAX_RECIPE_IMAGES) {
        throw BadRequestException("Only $MAX_RECIPE_IMAGES images are allowed per recipe")
    }

    if (imageIds.isEmpty()) return emptySet()

    val imagePrefix = Service.s3Service.recipeImageObjectKeyPrefix(
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
        Service.s3Service.recipeImageObjectKey(
            userId = userPrincipal.id.toString(),
            recipeId = recipeId.toString(),
            imageId = imageId
        ) !in existingObjectKeys
    }

    return missingImageIds.mapTo(mutableSetOf()) { imageId ->
        val sizeBytes = imageSizes[imageId]
            ?: throw BadRequestException("Upload size is required for image $imageId")

        val objectKey = Service.s3Service.recipeImageObjectKey(
            userId = userPrincipal.id.toString(),
            recipeId = recipeId.toString(),
            imageId = imageId
        )

        ImageUploadResponse(
            imageId = imageId,
            uploadUrl = Service.s3Service.getPresignedUrl(
                operation = PresignedOperation.PUT,
                objectKey = objectKey,
                sizeBytes = sizeBytes
            ),
            headers = mapOf(
                "Content-Type" to "image/png",
                "If-None-Match" to "*"
            )
        )
    }
}
