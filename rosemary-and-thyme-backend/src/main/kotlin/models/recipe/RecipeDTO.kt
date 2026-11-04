package rosemary_and_thyme.models.recipe

import kotlinx.serialization.Serializable
import java.time.LocalDateTime
import javax.print.attribute.standard.Sides

/**
 * Serializable representation of a recipe returned by the API.
 * Item data is stored in maps, while the corresponding identifier lists preserve display order.
 */
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

/**
 * Structured value stored in the `public.recipes.content` JSON column.
 * It is combined with the relational recipe columns when constructing [RecipeDTO].
 */
@Serializable
data class RecipeContentDTO(
    val images: Map<String, RecipeImageDTO> = emptyMap(),
    val ingredients: Map<String, IngredientDTO> = emptyMap(),
    val instructions: Map<String, InstructionDTO> = emptyMap(),
    val ingredientsOrder: List<String> = emptyList(),
    val instructionsOrder: List<String> = emptyList(),
    val heroImagesOrder: List<String> = emptyList()
)

/**
 * Image referenced by a recipe.
 *
 * @property id image identifier used by recipe ordering and instruction references
 * @property url URL of the uploaded image
 */
@Serializable
data class RecipeImageDTO(
    val id: String,
    val objectKey: String
)

/** Ingredient used by a recipe. */
@Serializable
data class IngredientDTO(
    val id: String,
    val name: String,
    val amount: Double
)

/** Recipe instruction with references to its associated images. */
@Serializable
data class InstructionDTO(
    val id: String,
    val text: String,
    val images: List<String>
)
