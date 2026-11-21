package rosemary_and_thyme.models.requests

import kotlinx.serialization.Serializable

/**
 * Requests direct-upload grants for the complete intended recipe image set.
 *
 * @property imageIds client-generated image identifiers intended for the recipe
 * @property imageSizes exact file sizes in bytes, keyed by image ID, required for images not yet in R2
 */
@Serializable
data class PresignRecipeImagesRequest(
    val imageIds: Set<String>,
    val imageSizes: Map<String, Long> = emptyMap()
)
