package rosemaryandthyme.backend.models.recipes

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.LocalDate
import java.util.UUID

/** One card's worth of data for the global recipe feed (home page) - a lighter projection of [RecipeDTO] with just what a feed card needs, plus a single… */
data class RecipeFeedDTO(
    val id: UUID,
    val name: String,
    val description: String,
    val likes: Long,
    val userName: String,
    val createDate: LocalDate,
    val heroImageUrl: String?,
    @get:JsonProperty("isPrivate")
    val isPrivate: Boolean
)
