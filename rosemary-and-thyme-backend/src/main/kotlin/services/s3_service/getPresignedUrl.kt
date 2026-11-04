package rosemary_and_thyme.services.s3_service

import aws.sdk.kotlin.services.s3.model.GetObjectRequest
import aws.sdk.kotlin.services.s3.model.PutObjectRequest
import aws.sdk.kotlin.services.s3.presigners.presignGetObject
import aws.sdk.kotlin.services.s3.presigners.presignPutObject
import io.ktor.server.plugins.BadRequestException
import rosemary_and_thyme.services.S3Service
import rosemary_and_thyme.services.Service
import kotlin.time.Duration.Companion.minutes

enum class PresignedOperation {
    PUT,
    GET;

    companion object {
        fun from(value: String): PresignedOperation =
            entries.firstOrNull {
                it.name.equals(value, ignoreCase = true)
            } ?: throw BadRequestException(
                "Unsupported S3 operation: $value"
            )
    }
}

suspend fun S3Service.getPresignedUrl(operation: String, objectKey: String): String {


    val parsedOperation = PresignedOperation.from(operation)

    return when (parsedOperation) {
        PresignedOperation.PUT -> {
            val request = PutObjectRequest {
                bucket = Service.s3Bucket
                key = objectKey
                this.contentType = "image/png"
            }
            Service.s3.presignPutObject(request,10.minutes).url.toString()
        }
        PresignedOperation.GET->{

            val request = GetObjectRequest {
                bucket = Service.s3Bucket
                key = objectKey
            }
            Service.s3.presignGetObject(request,10.minutes).url.toString()
        }

    }


}