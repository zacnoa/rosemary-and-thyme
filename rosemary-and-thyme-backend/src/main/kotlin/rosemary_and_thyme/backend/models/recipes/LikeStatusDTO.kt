package rosemary_and_thyme.backend.models.recipes

/** Response body for `POST /recipe/{id}/like` and `DELETE /recipe/{id}/like` (see [rosemary_and_thyme.backend.controllers.RecipeController]) - lets the… */
data class LikeStatusDTO(
    val liked: Boolean,
    val likes: Long
)
