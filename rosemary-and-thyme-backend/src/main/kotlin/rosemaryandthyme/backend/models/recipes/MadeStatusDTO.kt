package rosemaryandthyme.backend.models.recipes

/** Response body for `POST /recipe/{id}/made`. */
data class MadeStatusDTO(
    val timesMade: Long
)
