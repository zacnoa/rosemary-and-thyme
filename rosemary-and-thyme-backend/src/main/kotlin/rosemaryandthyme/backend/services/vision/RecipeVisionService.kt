package rosemaryandthyme.backend.services.vision

import io.github.bucket4j.Bandwidth
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.ByteArrayResource
import org.springframework.http.MediaType
import org.springframework.http.client.MultipartBodyBuilder
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Service
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestClientResponseException
import org.springframework.web.multipart.MultipartFile
import rosemaryandthyme.backend.exceptions.RecipeVisionComputeException
import rosemaryandthyme.backend.exceptions.RecipeVisionException
import rosemaryandthyme.backend.exceptions.RecipeVisionTimeoutException
import rosemaryandthyme.backend.exceptions.RecipeVisionUnavailableException
import rosemaryandthyme.backend.exceptions.TooManyRequestsException
import rosemaryandthyme.backend.models.vision.ExtractedRecipeDTO
import rosemaryandthyme.backend.models.vision.RecipeVisionResponse
import rosemaryandthyme.backend.services.ratelimit.RateLimiterService
import java.net.SocketTimeoutException
import java.net.http.HttpTimeoutException
import java.time.Duration
import java.util.UUID

/** Sends one source image to the Python vision service. */
@Service
class RecipeVisionService(
    private val rateLimiter: RateLimiterService,
    @Value($$"${recipe-vision.base-url}") baseUrl: String,
    @Value($$"${recipe-vision.api-key:}") private val apiKey: String,
    @Value($$"${recipe-vision.connect-timeout}") connectTimeout: Duration,
    @Value($$"${recipe-vision.read-timeout}") readTimeout: Duration
) {

    private val extractionLimit = Bandwidth.builder()
        .capacity(2)
        .refillIntervally(2, Duration.ofDays(1))
        .build()

    private val client = RestClient.builder()
        .baseUrl(baseUrl)
        .requestFactory(
            SimpleClientHttpRequestFactory().apply {
                setConnectTimeout(connectTimeout)
                setReadTimeout(readTimeout)
            }
        )
        .build()

    /** Extracts a normalized recipe without retaining the uploaded source image. */
    fun extractRecipe(image: MultipartFile, userId: UUID): ExtractedRecipeDTO {
        require(!image.isEmpty) { "Recipe image must not be empty" }

        val contentType = image.contentType?.let(MediaType::parseMediaType)
            ?: throw IllegalArgumentException("Recipe image must have a content type")
        require(contentType == MediaType.IMAGE_JPEG || contentType == MediaType.IMAGE_PNG) {
            "Only JPEG and PNG recipe images are supported"
        }
        if (!rateLimiter.tryConsume("recipe-extraction-user", userId.toString(), extractionLimit)) {
            throw TooManyRequestsException(
                "You can create up to 2 digital recipe drafts per day. Please try again later."
            )
        }

        val imageResource = object : ByteArrayResource(image.bytes) {
            override fun getFilename(): String = image.originalFilename
                ?.takeIf { it.isNotBlank() }
                ?: if (contentType == MediaType.IMAGE_PNG) "recipe.png" else "recipe.jpg"
        }
        val multipartBody = MultipartBodyBuilder().apply {
            part("image", imageResource).contentType(contentType)
        }.build()

        try {
            val request = client.post()
                .uri("/recipe-vision")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .apply {
                    if (apiKey.isNotBlank()) header("X-API-Key", apiKey)
                }

            return request
                .body(multipartBody)
                .retrieve()
                .body(RecipeVisionResponse::class.java)
                ?.recipe
                ?: throw RecipeVisionException("Recipe vision returned an empty response")
        } catch (error: ResourceAccessException) {
            if (error.hasTimeoutCause()) {
                throw RecipeVisionTimeoutException("Recipe extraction timed out", error)
            }
            throw RecipeVisionUnavailableException("Recipe vision service is unavailable", error)
        } catch (error: RestClientResponseException) {
            if (error.statusCode.is5xxServerError) {
                throw RecipeVisionComputeException(
                    "Recipe vision could not process this image. Please try again later.",
                    error
                )
            }
            throw RecipeVisionException(
                "Recipe vision failed with status ${error.statusCode.value()}",
                error
            )
        } catch (error: RestClientException) {
            throw RecipeVisionComputeException(
                "Recipe vision could not process this image. Please try again later.",
                error
            )
        }
    }

    private fun Throwable.hasTimeoutCause(): Boolean = generateSequence(this) { it.cause }
        .any { it is SocketTimeoutException || it is HttpTimeoutException }
}
