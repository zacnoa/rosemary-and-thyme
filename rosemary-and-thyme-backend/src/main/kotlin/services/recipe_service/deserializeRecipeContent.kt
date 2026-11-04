package rosemary_and_thyme.services.recipe_service

import kotlinx.serialization.json.Json
import rosemary_and_thyme.models.responses.RecipeContentDTO
import rosemary_and_thyme.services.RecipeService

fun RecipeService.deserializeRecipeContent(json: String): RecipeContentDTO =
    Json.decodeFromString(json)