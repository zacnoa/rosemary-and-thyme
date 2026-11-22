package rosemary_and_thyme.services.s3_service

import com.auth0.jwt.JWT
import rosemary_and_thyme.services.CryptoService
import rosemary_and_thyme.services.S3Service
import java.time.Instant


/**
 * Creates a Worker URL for one recipe image, with a 15-minute RS256 access token in the query string.
 * The token binds [userId], [recipeId], and [imageId] to the URL path; the Worker must verify all
 * three claims along with the signature, issuer, audience, and expiration before serving the image.
 * Call this only after the viewer's access to the recipe has been checked.
 *
 * @param workerBaseUrl Worker origin without a trailing slash
 * @param userId owner of the recipe and image
 * @param recipeId recipe containing the image
 * @param imageId image for which access is granted
 * @return a temporary Worker URL, not an R2 presigned URL or a bare JWT
 */
fun S3Service.mediaAccessToken(workerBaseUrl: String, userId: String, recipeId: String, imageId: String): String =

    JWT.create()
        .withIssuer("rosemary_and_thyme")
        .withAudience("rosemary_and_thyme_worker")
        .withClaim("userId", userId)
        .withClaim("recipeId", recipeId)
        .withClaim("imageId", imageId)
        .withExpiresAt(Instant.now().plusSeconds(15 * 60))
        .sign(CryptoService.jwtAlgorithm).let { jwt ->

            "$workerBaseUrl/recipe-images/${userId}/${recipeId}/${imageId}?token=${jwt}"
        }
