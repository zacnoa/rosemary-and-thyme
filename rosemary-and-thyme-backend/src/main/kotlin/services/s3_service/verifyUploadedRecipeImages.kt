package rosemary_and_thyme.services.s3_service

import aws.sdk.kotlin.services.s3.listObjectsV2
import rosemary_and_thyme.models.exceptions.RecipeImageUploadException
import rosemary_and_thyme.services.S3Service
import rosemary_and_thyme.services.Service
import java.util.UUID

private const val MAX_IMAGE_SIZE_BYTES = 10L * 1024 * 1024

/**
 * Verifies that every expected recipe image exists in R2 and has an accepted size.
 * Exact matching is used for draft completion, while updates may retain additional old objects
 * until their database changes have been committed and cleanup can run safely.
 *
 * @param recipeId recipe whose image objects are checked
 * @param userId owner used to derive the recipe image prefix
 * @param imageIds expected client-generated image identifiers
 * @param allowAdditionalObjects whether R2 may contain objects not referenced by [imageIds]
 * @throws RecipeImageUploadException if expected objects are missing, unexpected, empty, or too large
 */
suspend fun S3Service.verifyUploadedRecipeImages(
    recipeId: UUID,
    userId: UUID,
    imageIds: Set<String>,
    allowAdditionalObjects: Boolean
) {
    if (imageIds.isEmpty() && allowAdditionalObjects) return

    val expectedKeys = imageIds.mapTo(mutableSetOf()) { imageId ->
        Service.s3Service.recipeImageKey(
            userId = userId.toString(),
            recipeId = recipeId.toString(),
            imageId = imageId
        )
    }

    val imagePrefix = Service.s3Service.recipeImagePrefix(
        userId = userId.toString(),
        recipeId = recipeId.toString()
    )

    val uploadedObjects = Service.s3Client.listObjectsV2 {
        bucket = Service.s3Bucket
        prefix = imagePrefix
        maxKeys = if (allowAdditionalObjects) 1000 else expectedKeys.size + 1
    }.contents.orEmpty()

    val uploadedKeys = uploadedObjects
        .mapNotNull { uploadedObject -> uploadedObject.key }
        .toSet()

    val keysAreValid = if (allowAdditionalObjects) {
        uploadedKeys.containsAll(expectedKeys)
    } else {
        uploadedKeys == expectedKeys
    }

    if (!keysAreValid) {
        throw RecipeImageUploadException(
            "Uploaded recipe images do not contain all expected images"
        )
    }

    val expectedObjectsHaveInvalidSize = uploadedObjects.any { uploadedObject ->
        uploadedObject.key in expectedKeys &&
                (uploadedObject.size ?: 0L) !in 1..MAX_IMAGE_SIZE_BYTES
    }

    if (expectedObjectsHaveInvalidSize) {
        throw RecipeImageUploadException(
            "One or more recipe images have an invalid size"
        )
    }
}
