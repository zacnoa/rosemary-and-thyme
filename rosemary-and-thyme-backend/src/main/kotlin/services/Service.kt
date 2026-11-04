package rosemary_and_thyme.services

import aws.sdk.kotlin.services.s3.S3Client
import org.jooq.DSLContext

/**
 * Central access point for application services and their shared database and object-storage clients.
 *
 * Service operations are organized as extension functions on [RecipeService], [UserService],
 * [AuthService], and [S3Service]. The required clients must be initialized during application startup
 * before a service operation uses them.
 */
object Service {
    private var dslContextNullable: DSLContext? = null
    private var s3ClientNullable: S3Client?=null
    var s3BucketNullable:String?=null

    val recipeService = RecipeService()
    val userService = UserService()
    val authService = AuthService()
    val s3Service = S3Service()

    /**
     * The shared jOOQ context used by service operations.
     *
     * @throws IllegalStateException if [Service] has not been initialized
     */
    val dsl: DSLContext
        get() = checkNotNull(dslContextNullable) { "Service has not been initialized" }

    /**
     * Initializes the service layer with the application's jOOQ context.
     *
     * @param dsl context used for database access
     * @throws IllegalStateException if the service layer is already initialized
     */
    fun initializeDatabase(dsl: DSLContext) {
        check(dslContextNullable == null) {
            "Database has already been initialized"
        }
        dslContextNullable = dsl
    }

    /**
     * The shared S3-compatible client used for Cloudflare R2 operations.
     *
     * @throws IllegalStateException if the client has not been initialized
     */
    val s3Client:S3Client 
        get()=checkNotNull(s3ClientNullable){"S3 has not been initialized"}

    /**
     * Name of the R2 bucket used for recipe images.
     *
     * @throws IllegalStateException if the bucket has not been initialized
     */
    val s3Bucket:String
        get()= checkNotNull(s3BucketNullable){"S3 bucket has not been initialized"}

    /**
     * Initializes the object-storage client and its recipe image bucket.
     *
     * @param client S3-compatible client configured for Cloudflare R2
     * @param bucket bucket used to store recipe images
     * @throws IllegalStateException if the S3 client has already been initialized
     */
    fun initializeS3(client: S3Client, bucket:String){
        check(s3ClientNullable==null){
            "S3 client has already been initialized"
        }
        s3ClientNullable=client
        s3BucketNullable=bucket
    }

}


/** Namespace for recipe-related service operations. */
class RecipeService

/** Namespace for user account service operations. */
class UserService

/** Namespace for authentication and session service operations. */
class AuthService

/** Namespace for Cloudflare R2 object-storage operations. */
class S3Service

