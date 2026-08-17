package rosemary_and_thyme.backend.models.recipes

/**
 * Response body for `POST /recipe/{id}/like` and `DELETE /recipe/{id}/like` (see
 * [rosemary_and_thyme.backend.controllers.RecipeController]) - lets the frontend
 * update both the heart/thumbs-up state and the displayed count from one response,
 * without re-fetching the whole [RecipeDTO].
 *
 * @property liked whether the calling user now likes the recipe (`true` after a
 * successful like, `false` after a successful unlike)
 * @property likes the recipe's total like count after the change
 */
data class LikeStatusDTO(
    val liked: Boolean,
    val likes: Long
)
