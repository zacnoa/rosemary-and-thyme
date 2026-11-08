package rosemary_and_thyme.services.s3_service

import aws.sdk.kotlin.services.s3.deleteObjects
import aws.sdk.kotlin.services.s3.model.Delete
import aws.sdk.kotlin.services.s3.model.ObjectIdentifier
import rosemary_and_thyme.services.S3Service
import rosemary_and_thyme.services.Service

private const val MAX_DELETE_OBJECTS_PER_REQUEST = 1000

/**
 * Deletes recipe image objects from R2 in batches supported by the S3 API.
 * Deleting an object that no longer exists is treated as a successful idempotent operation.
 *
 * @param objectKeys complete R2 object keys to delete
 * @throws IllegalStateException if R2 reports one or more object-level deletion failures
 */
suspend fun S3Service.deleteImages(objectKeys: Set<String>) {
    objectKeys.chunked(MAX_DELETE_OBJECTS_PER_REQUEST).forEach { objectKeyBatch ->
        val response = Service.s3Client.deleteObjects {
            bucket = Service.s3Bucket
            delete = Delete {
                objects = objectKeyBatch.map { objectKey ->
                    ObjectIdentifier {
                        key = objectKey
                    }
                }
                quiet = true
            }
        }

        val failedDeletions = response.errors.orEmpty()
        if (failedDeletions.isNotEmpty()) {
            throw IllegalStateException(
                "R2 failed to delete ${failedDeletions.size} recipe image objects"
            )
        }
    }
}
