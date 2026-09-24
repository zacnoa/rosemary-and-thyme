package rosemaryandthyme.backend.models.vision

import com.fasterxml.jackson.annotation.JsonProperty
import java.math.BigDecimal

/** Response returned by the Python recipe-vision service. */
data class RecipeVisionResponse(
    val recipe: ExtractedRecipeDTO
)

/** Normalized recipe produced by the Recipe Vision model. */
data class ExtractedRecipeDTO(
    @JsonProperty("source_name") val sourceName: String?,
    val name: String?,
    val language: String?,
    val portions: Int?,
    @JsonProperty("cook_time") val cookTime: String?,
    val difficulty: Int?,
    @JsonProperty("side_notes") val sideNotes: String?,
    val ingredients: List<ExtractedIngredientDTO>,
    val instructions: List<ExtractedInstructionDTO>
)

/** One normalized ingredient. */
data class ExtractedIngredientDTO(
    val name: String,
    val amount: BigDecimal?,
    @JsonProperty("measuring_unit") val measuringUnit: String?
)

/** One normalized instruction with its exact source transcription retained. */
data class ExtractedInstructionDTO(
    val text: String
)
