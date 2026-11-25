package rosemary_and_thyme.models.requests

import kotlinx.serialization.Serializable
import rosemary_and_thyme.models.responses.IngredientDTO
import rosemary_and_thyme.models.responses.InstructionDTO

/**
 * Client-editable data used to replace an existing recipe.
 * Resource identity, ownership, creation time, and counters are controlled by the server.
 *
 * @property cookTime display text for the cooking duration, up to 20 characters
 * @property imageIds complete set of image identifiers retained by the replacement recipe
 * @property version recipe version read by the client; stale replacements are rejected
 */
@Serializable
data class PutRecipeRequest(
    val title: String,
    val description: String,
    val portions: Short,
    val difficulty: Short,
    val sideNote: String,
    val imageIds: Set<String>,
    val ingredients: Map<String, IngredientDTO>,
    val instructions: Map<String, InstructionDTO>,
    val ingredientsOrder: List<String>,
    val instructionsOrder: List<String>,
    val heroImagesOrder: List<String>,
    val isPrivate: Boolean,
    val version: Long,
    val cookTime: String
)
