package rosemary_and_thyme.models.requests

import kotlinx.serialization.Serializable

/**
 * Requests direct-upload grants for the complete intended recipe image set.
 *
 * @property imageIds client-generated image identifiers intended for the recipe
 */
@Serializable
data class PresignRecipeImagesRequest(
    val imageIds: Set<String>
)
