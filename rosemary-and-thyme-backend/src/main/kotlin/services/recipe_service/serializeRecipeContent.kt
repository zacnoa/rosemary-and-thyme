package rosemary_and_thyme.services.recipe_service

import kotlinx.serialization.json.Json
import rosemary_and_thyme.models.recipe.RecipeContentDTO
import rosemary_and_thyme.services.RecipeService

/**
 * Serializes structured recipe content for storage in the database.
 *
 * @param content recipe content to serialize
 * @return the content encoded as a JSON string
 */
fun RecipeService.serializeRecipeContent(content: RecipeContentDTO): String =
    Json.encodeToString(RecipeContentDTO.serializer(), content)
