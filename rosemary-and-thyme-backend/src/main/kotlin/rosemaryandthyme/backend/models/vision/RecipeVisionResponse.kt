package rosemaryandthyme.backend.models.vision

import com.fasterxml.jackson.annotation.JsonProperty
import java.math.BigDecimal

/** Response returned by the Python recipe-vision service. */
data class RecipeVisionResponse(
    val recipe: ExtractedRecipeDTO
)

/** Normalized recipe produced by the vision and parser model passes. */
data class ExtractedRecipeDTO(
    @JsonProperty("source_name") val sourceName: String?,
    val name: String?,
    val language: String?,
    val description: String?,
    val portions: Int?,
    @JsonProperty("cook_time") val cookTime: String?,
    val difficulty: Int?,
    @JsonProperty("side_notes") val sideNotes: String?,
    val ingredients: List<ExtractedIngredientDTO>,
    val instructions: List<ExtractedInstructionDTO>,
    val warnings: List<String>,
    val confidence: Double,
    @JsonProperty("requires_review") val requiresReview: Boolean
)

/** One normalized ingredient with its exact source transcription retained. */
data class ExtractedIngredientDTO(
    @JsonProperty("source_text") val sourceText: String,
    val name: String?,
    val amount: BigDecimal?,
    @JsonProperty("measuring_unit") val measuringUnit: String?,
    val confidence: Double,
    val warnings: List<String>
)

/** One normalized instruction with its exact source transcription retained. */
data class ExtractedInstructionDTO(
    @JsonProperty("source_text") val sourceText: String,
    val text: String?,
    val confidence: Double,
    val warnings: List<String>
)
