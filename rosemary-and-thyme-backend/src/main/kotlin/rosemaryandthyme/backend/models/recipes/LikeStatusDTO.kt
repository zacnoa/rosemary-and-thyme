package rosemaryandthyme.backend.models.recipes

/** Response body for `POST /recipe/{id}/like` and `DELETE /recipe/{id}/like` (see [rosemaryandthyme.backend.controllers.RecipeController]) - lets the… */
data class LikeStatusDTO(
    val liked: Boolean,
    val likes: Long
)
