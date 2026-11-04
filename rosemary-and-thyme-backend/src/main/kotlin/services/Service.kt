package rosemary_and_thyme.services

import aws.sdk.kotlin.services.s3.S3Client
import org.jooq.DSLContext

/**
 * Central access point for application services and their shared [DSLContext].
 *
 * Service operations are organized as extension functions on [RecipeService], [UserService],
 * and [AuthService]. The object must be initialized before any database-backed operation runs.
 */
object Service {
    private var dslContext: DSLContext? = null
    private var s3Client: S3Client?=null
    var s3Bucket:String?=null

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
        get() = checkNotNull(dslContext) { "Service has not been initialized" }

    /**
     * Initializes the service layer with the application's jOOQ context.
     *
     * @param dsl context used for database access
     * @throws IllegalStateException if the service layer is already initialized
     */
    fun initializeDatabase(dsl: DSLContext) {
        check(dslContext == null) {
            "Database has already been initialized"
        }
        dslContext = dsl
    }

    val s3:S3Client 
        get()=checkNotNull(s3Client){"S3 has not been initialized"}
        
    fun initializeS3(client: S3Client, bucket:String){
        check(s3Client==null){
            "S3 client has already been initialized"
        }
        s3Client=client
        s3Bucket=bucket
    }

}


/** Namespace for recipe-related service operations. */
class RecipeService

/** Namespace for user account service operations. */
class UserService

/** Namespace for authentication and session service operations. */
class AuthService

class S3Service

