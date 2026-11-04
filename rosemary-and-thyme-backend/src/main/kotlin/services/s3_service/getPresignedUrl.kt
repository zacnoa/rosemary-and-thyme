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
 * PUT URLs are signed with `image/png` as the required content type.
 *
 * @param operation object operation authorized by the URL
 * @param objectKey complete key of the target object inside the configured bucket
 * @return temporary URL containing the request signature
 * @throws IllegalStateException if the shared S3 client has not been initialized
 */
suspend fun S3Service.getPresignedUrl(operation: PresignedOperation, objectKey: String): String {



    return when (operation) {
        PresignedOperation.PUT -> {
            val request = PutObjectRequest {
                bucket = Service.s3BucketNullable
                key = objectKey
                this.contentType = "image/png"
            }
            Service.s3Client.presignPutObject(request,10.minutes).url.toString()
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
