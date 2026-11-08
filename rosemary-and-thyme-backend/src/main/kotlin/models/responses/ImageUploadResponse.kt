package rosemary_and_thyme.models.responses

import kotlinx.serialization.Serializable

/**
 * Presigned direct-upload grant for one recipe image.
 *
 * @property imageId client-generated identifier of the corresponding image
 * @property uploadUrl temporary R2 PUT URL that accepts `image/png`
 */
@Serializable
data class ImageUploadResponse(
    val imageId: String,
    val uploadUrl: String
)