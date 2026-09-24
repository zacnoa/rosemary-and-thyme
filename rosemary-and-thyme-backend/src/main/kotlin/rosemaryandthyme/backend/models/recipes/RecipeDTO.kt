package rosemaryandthyme.backend.models.recipes

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.LocalDate
import java.util.UUID

/** Full representation of a recipe as sent to/from the frontend - mirrors the recipe editor's model on the frontend side. */
data class RecipeDTO(
    val id: UUID,
    val userId: UUID,
    val userName: String,
    val createDate: LocalDate,
    val name: String,
    val description: String,
    val likes: Long,
    val timesMade: Long,
    val liked: Boolean,
    val portions: Int,
    val cookTime: String,
    val difficulty: Int,
    val sideNotes: String?,
    val images: Map<UUID, RecipeImageDTO>,
    val ingredients: Map<UUID, IngredientDTO>,
    val instructions: Map<UUID, InstructionDTO>,
    val ingredientsOrder: List<UUID>,
    val instructionsOrder: List<UUID>,
    val heroImagesOrder: List<UUID>,
    @get:JsonProperty("isPrivate")
    val isPrivate: Boolean = false
)
