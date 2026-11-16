package rosemary_and_thyme.models.responses

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Summary of a recipe in the search response.
 *
 * @property isPublic visibility flag serialized as `public`
 * @property likes number of likes at the time of the search
 * @property imageId first identifier in the recipe's `imageIds` array, or `null` if it has no images
 * @property imageUrl Worker URL with a token scoped to [imageId], populated by the search route
 */
@Serializable
data class RecipeFeedItem(
    val recipeId: String,
    val userId: String,
    val username: String,
    @SerialName("public") val isPublic: Boolean,
    val title: String,
    val likes: Int,
    val description: String,
    val difficulty: Short,
    val portions: Short,
    val createdDate: String,
    val timesMade: Int,
    val imageId: String?,
    val imageUrl: String?
)
