package rosemary_and_thyme.backend.models.recipes

import java.math.BigDecimal
import java.util.UUID

/**
 * A single ingredient line of a recipe.
 *
 * @property id the ingredient's id
 * @property name the ingredient's name (e.g. "flour")
 * @property amount the quantity, as a decimal so fractional amounts (e.g. 0.5) round-trip exactly
 * @property measuringUnit the unit the amount is expressed in (e.g. "g", "cups")
 */
data class IngredientDTO(
    val id: UUID,
    val name: String,
    val amount: BigDecimal,
    val measuringUnit: String
)
