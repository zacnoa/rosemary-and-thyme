package rosemary_and_thyme.services.s3_service

import aws.sdk.kotlin.services.s3.model.GetObjectRequest
import aws.sdk.kotlin.services.s3.model.PutObjectRequest
import aws.sdk.kotlin.services.s3.presigners.presignGetObject
import aws.sdk.kotlin.services.s3.presigners.presignPutObject
import io.ktor.server.plugins.BadRequestException
import rosemary_and_thyme.services.S3Service
import rosemary_and_thyme.services.Service
import kotlin.time.Duration.Companion.minutes

/** Supported R2 operations for which the service can issue a presigned URL. */
enum class PresignedOperation {
    PUT,
    GET;

    companion object {
        /**
         * Resolves an operation name without regard to letter case.
         *
         * @param value operation name supplied by the caller
         * @return matching presigned operation
         * @throws BadRequestException if the operation is unsupported
         */
        fun from(value: String): PresignedOperation =
            entries.firstOrNull {
                it.name.equals(value, ignoreCase = true)
            } ?: throw BadRequestException(
                "Unsupported S3 operation: $value"
            )
    }
}

/**
 * Generates a presigned R2 URL valid for ten minutes for a single object and operation.
 * PUT URLs require `image/png`, the exact content length, and `If-None-Match: *`.
 *
 * @param operation object operation authorized by the URL
 * @param objectKey complete key of the target object inside the configured bucket
 * @param sizeBytes exact file size for PUT, between one byte and [MAX_IMAGE_SIZE_BYTES]
 * @return temporary URL containing the request signature
 * @throws IllegalStateException if the shared S3 client has not been initialized
 * @throws BadRequestException if the PUT size is missing or outside the accepted range
 */
suspend fun S3Service.getPresignedUrl(
    operation: PresignedOperation,
    objectKey: String,
    sizeBytes: Long? = null
): String {
    return when (operation) {
        PresignedOperation.PUT -> {
            val uploadSize = sizeBytes?.takeIf { it in 1L..MAX_IMAGE_SIZE_BYTES }
                ?: throw BadRequestException("Image size must be between 1 byte and 10 MiB")

            val request = PutObjectRequest {
                bucket = Service.s3Bucket
                key = objectKey
                contentType = "image/png"
                contentLength = uploadSize
                ifNoneMatch = "*"
            }
            Service.s3Client.presignPutObject(request, 10.minutes).url.toString()
        }
        PresignedOperation.GET->{

            val request = GetObjectRequest {
                bucket = Service.s3BucketNullable
                key = objectKey
            }
            Service.s3Client.presignGetObject(request,10.minutes).url.toString()
        }

    }
}
