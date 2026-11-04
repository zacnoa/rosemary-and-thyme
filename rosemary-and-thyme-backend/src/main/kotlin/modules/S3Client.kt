package rosemary_and_thyme.modules

import aws.sdk.kotlin.runtime.auth.credentials.StaticCredentialsProvider
import aws.sdk.kotlin.services.s3.S3Client
import aws.smithy.kotlin.runtime.auth.awscredentials.Credentials
import aws.smithy.kotlin.runtime.net.url.Url
import io.ktor.server.application.Application



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