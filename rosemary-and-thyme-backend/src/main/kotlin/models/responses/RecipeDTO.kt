package rosemary_and_thyme.models.responses

import kotlinx.serialization.Serializable

/**
 * Serializable representation of a recipe returned by the API.
 * Ingredients and instructions are stored in maps, while the order lists retain their identifiers.
 *
 * @property imageIds image IDs mapped to temporary Worker URLs in the HTTP detail response;
 * stored recipe content and write requests keep image IDs as a set
 * @property version current edit version to send with the next recipe replacement
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
    val createdDate: String,

    val imageIds: Map<String, String>,
    val ingredients: Map<String, IngredientDTO>,
    val instructions: Map<String, InstructionDTO>,

    val ingredientsOrder: List<String>,
    val instructionsOrder: List<String>,
    val heroImagesOrder: List<String>,

    val isPrivate: Boolean,
    val version: Long
)

/**
 * Structured value stored in the `public.recipes.content` JSON column.
 * It is combined with the relational recipe columns when constructing [RecipeDTO].
 *
 * @property imageIds identifiers of every image belonging to the recipe
 */
@Serializable
data class RecipeContentDTO(
    val imageIds: Set<String> = emptySet(),
    val ingredients: Map<String, IngredientDTO> = emptyMap(),
    val instructions: Map<String, InstructionDTO> = emptyMap(),
    val ingredientsOrder: List<String> = emptyList(),
    val instructionsOrder: List<String> = emptyList(),
    val heroImagesOrder: List<String> = emptyList()
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
