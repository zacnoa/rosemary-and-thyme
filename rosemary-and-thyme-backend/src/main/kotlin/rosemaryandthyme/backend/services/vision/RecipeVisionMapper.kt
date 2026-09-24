package rosemaryandthyme.backend.services.vision

import rosemaryandthyme.backend.models.recipes.IngredientDTO
import rosemaryandthyme.backend.models.recipes.InstructionDTO
import rosemaryandthyme.backend.models.recipes.RecipeWriteDTO
import rosemaryandthyme.backend.models.vision.ExtractedRecipeDTO
import java.math.BigDecimal
import java.util.UUID

/** Converts model output into the existing editable recipe representation. */
object RecipeVisionMapper {

    fun toRecipeWriteDTO(extracted: ExtractedRecipeDTO): RecipeWriteDTO {
        val ingredientEntries = extracted.ingredients.take(50).map { extractedIngredient ->
            val id = UUID.randomUUID()
            id to IngredientDTO(
                id = id,
                name = extractedIngredient.name
                    .orFallback("Unknown ingredient")
                    .take(50),
                amount = extractedIngredient.amount
                    ?.takeIf { it >= BigDecimal.ZERO }
                    ?: BigDecimal.ZERO,
                measuringUnit = extractedIngredient.measuringUnit
                    ?.trim()
                    .orEmpty()
                    .take(20)
            )
        }
        val instructionEntries = extracted.instructions.take(50).map { extractedInstruction ->
            val id = UUID.randomUUID()
            id to InstructionDTO(
                id = id,
                text = extractedInstruction.text,
                images = emptyList()
            )
        }

        return RecipeWriteDTO(
            name = extracted.name.orFallback(extracted.sourceName, "Untitled recipe").take(100),
            description = "",
            portions = extracted.portions?.takeIf { it > 0 } ?: 1,
            cookTime = extracted.cookTime.orEmpty().take(50),
            difficulty = extracted.difficulty?.takeIf { it in 1..5 } ?: 1,
            sideNotes = extracted.sideNotes,
            images = emptyMap(),
            ingredients = ingredientEntries.toMap(LinkedHashMap()),
            instructions = instructionEntries.toMap(LinkedHashMap()),
            ingredientsOrder = ingredientEntries.map { it.first },
            instructionsOrder = instructionEntries.map { it.first },
            heroImagesOrder = emptyList()
        )
    }

    private fun String?.orFallback(vararg fallbacks: String?): String =
        sequenceOf(this, *fallbacks)
            .filterNotNull()
            .map(String::trim)
            .firstOrNull { it.isNotEmpty() }
            .orEmpty()
}
