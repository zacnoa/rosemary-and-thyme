package rosemary_and_thyme.backend.models.recipes

import java.util.UUID

/** Client-controlled recipe fields. Server-owned metadata belongs only to [RecipeDTO]. */
data class RecipeWriteDTO(
    val name: String,
    val description: String,
    val portions: Int,
    val cookTime: String,
    val difficulty: Int,
    val sideNotes: String?,
    val images: Map<UUID, RecipeImageDTO>,
    val ingredients: Map<UUID, IngredientDTO>,
    val instructions: Map<UUID, InstructionDTO>,
    val ingredientsOrder: List<UUID>,
    val instructionsOrder: List<UUID>,
    val heroImagesOrder: List<UUID>
)
