package rosemary_and_thyme.backend.models.recipes

import java.math.BigDecimal
import java.util.UUID

/** A single ingredient line of a recipe. */
data class IngredientDTO(
    val id: UUID,
    val name: String,
    val amount: BigDecimal,
    val measuringUnit: String
)
