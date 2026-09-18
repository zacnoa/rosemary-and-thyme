package rosemary_and_thyme.backend.models.recipes

import java.util.UUID

/** Describes constraints and ordering for a cursor-paginated recipe feed. */
data class RecipeFeedFilter(
    val q: String? = null,
    val ownerId: UUID? = null,
    val likedByUserId: UUID? = null,
    val excludePrivate: Boolean = false,
    val sort: RecipeFeedSort
)

/** Supported stable ordering strategies for a recipe feed. */
enum class RecipeFeedSort {
    NAME_ASC,
    RELEVANCE_DESC,
    LIKES_DESC,
    LIKED_AT_DESC
}
