package rosemary_and_thyme.backend.models.recipes

import java.util.UUID

/** A single step of a recipe's instructions. */
data class InstructionDTO(
    val id: UUID,
    val text: String,
    val images: List<UUID>
)
