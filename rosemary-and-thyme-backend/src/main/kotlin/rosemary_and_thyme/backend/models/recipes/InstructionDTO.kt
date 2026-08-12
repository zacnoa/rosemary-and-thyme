package rosemary_and_thyme.backend.models.recipes

import java.util.UUID

/**
 * A single step of a recipe's instructions.
 *
 * @property id the instruction step's id
 * @property text the step's instruction text
 * @property images ids of the [RecipeImageDTO] entries (see [RecipeDTO.images]) attached
 * to this step, in display order
 */
data class InstructionDTO(
    val id: UUID,
    val text: String,
    val images: List<UUID>
)
