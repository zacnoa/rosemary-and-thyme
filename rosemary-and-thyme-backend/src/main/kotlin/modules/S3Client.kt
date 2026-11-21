package rosemary_and_thyme.modules

import aws.sdk.kotlin.runtime.auth.credentials.StaticCredentialsProvider
import aws.sdk.kotlin.services.s3.S3Client
import aws.smithy.kotlin.runtime.auth.awscredentials.Credentials
import aws.smithy.kotlin.runtime.net.url.Url
import io.ktor.server.application.Application

/**
 * Creates an S3-compatible client configured for the application's Cloudflare R2 account.
 * Credentials and the account identifier are read from the `s3Client` application configuration.
 *
 * @return client used to sign and execute R2 object operations
 */
fun Application.configureS3Client(): S3Client {
    
    val clientConfig=environment.config.config("s3Client")
    val accountId=clientConfig.property("accountId").getString()
    val accessKeyId=clientConfig.property("accessKeyId").getString()
    val secretAccessKey=clientConfig.property("secretAccessKey").getString()
    
    val client= S3Client{
        region="auto"
        endpointUrl= Url.parse("https://$accountId.r2.cloudfarestorage.com")
        credentialsProvider= StaticCredentialsProvider(
            Credentials(
                accessKeyId = accessKeyId,
                secretAccessKey = secretAccessKey
            )
        )
    }
    return client


}
