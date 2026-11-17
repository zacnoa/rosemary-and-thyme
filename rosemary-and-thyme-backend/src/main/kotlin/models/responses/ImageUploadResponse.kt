package rosemary_and_thyme.models.responses

import kotlinx.serialization.Serializable

/**
 * Presigned direct-upload grant for one recipe image.
 *
 * @property imageId client-generated identifier of the corresponding image
 * @property uploadUrl temporary R2 PUT URL signed for the declared size and creation of a new object
 * @property headers headers the browser must send with the file body; it sets Content-Length automatically
 */
@Serializable
data class ImageUploadResponse(
    val imageId: String,
    val uploadUrl: String,
    val headers: Map<String, String>
)
