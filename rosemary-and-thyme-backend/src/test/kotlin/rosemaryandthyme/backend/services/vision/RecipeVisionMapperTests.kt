package rosemaryandthyme.backend.services.vision

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import rosemaryandthyme.backend.models.vision.ExtractedIngredientDTO
import rosemaryandthyme.backend.models.vision.ExtractedInstructionDTO
import rosemaryandthyme.backend.models.vision.ExtractedRecipeDTO
import java.math.BigDecimal

class RecipeVisionMapperTests {

    @Test
    fun `maps extracted output into an editable recipe`() {
        val extracted = ExtractedRecipeDTO(
            sourceName = "Palačinke",
            name = "Palačinke",
            language = "hr",
            portions = null,
            cookTime = null,
            difficulty = null,
            sideNotes = null,
            ingredients = listOf(
                ExtractedIngredientDTO(
                    name = "mlijeko",
                    amount = BigDecimal("0.5"),
                    measuringUnit = "l"
                )
            ),
            instructions = listOf(
                ExtractedInstructionDTO(
                    text = "Sve promiješati."
                )
            )
        )

        val recipe = RecipeVisionMapper.toRecipeWriteDTO(extracted)

        assertEquals("Palačinke", recipe.name)
        assertEquals(1, recipe.portions)
        assertEquals(1, recipe.difficulty)
        assertEquals(BigDecimal("0.5"), recipe.ingredients.values.single().amount)
        assertEquals("Sve promiješati.", recipe.instructions.values.single().text)
        assertEquals(recipe.ingredients.keys.toList(), recipe.ingredientsOrder)
        assertEquals(recipe.instructions.keys.toList(), recipe.instructionsOrder)
        assertTrue(recipe.images.isEmpty())
        assertTrue(recipe.heroImagesOrder.isEmpty())
    }
}
