package rosemary_and_thyme.backend.models.recipes

import java.util.UUID

/** A single image attached to a recipe, either as a hero image or as an instruction step image (the two are stored in different join tables but share this… */
data class RecipeImageDTO(
    val id: UUID,
    val url: String?
)
