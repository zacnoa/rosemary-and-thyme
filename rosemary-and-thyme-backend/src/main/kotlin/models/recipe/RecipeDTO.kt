package rosemary_and_thyme.models.recipe

import kotlinx.serialization.Serializable
import java.time.LocalDateTime
import javax.print.attribute.standard.Sides

@Serializable
data class RecipeDTO(
    val id: String,
    val userId: String,
    val userName: String,
    val title: String,
    val description: String,
    val likes: Int,
    val timesMade: Int,
    val portions: Short,
    val difficulty: Short,
    val sideNote: String,
    val createdDate:String,

    val images: Map<String, RecipeImageDTO>,
    val ingredients: Map<String, IngredientDTO>,
    val instructions: Map<String, InstructionDTO>,

    val ingredientsOrder: List<String>,
    val instructionsOrder: List<String>,
    val heroImagesOrder: List<String>,

    val isPrivate: Boolean
)

@Serializable
data class RecipeContentDTO(
    val images: Map<String, RecipeImageDTO> = emptyMap(),
    val ingredients: Map<String, IngredientDTO> = emptyMap(),
    val instructions: Map<String, InstructionDTO> = emptyMap(),
    val ingredientsOrder: List<String> = emptyList(),
    val instructionsOrder: List<String> = emptyList(),
    val heroImagesOrder: List<String> = emptyList()
)

@Serializable
data class RecipeImageDTO(
    val id: String,
    val url: String
)

@Serializable
data class IngredientDTO(
    val id: String,
    val name: String,
    val amount: Double
)

@Serializable
data class InstructionDTO(
    val id: String,
    val text: String,
    val images: List<String>
)