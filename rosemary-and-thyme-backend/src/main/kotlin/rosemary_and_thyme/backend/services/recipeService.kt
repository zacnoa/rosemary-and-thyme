package rosemary_and_thyme.backend.services

import org.springframework.stereotype.Service
import rosemary_and_thyme.backend.models.ApiResponse
import rosemary_and_thyme.backend.repositories.recipes.RecipeDTO
import rosemary_and_thyme.backend.repositories.recipes.RecipeRepository
import java.util.UUID


@Service
class RecipeService(private val recipeRepository: RecipeRepository) {

    fun findRecipeById(id: UUID): ApiResponse<RecipeDTO> =
        recipeRepository.fetchRecipe(id)

    fun saveRecipe(id:UUID,recipe: RecipeDTO) {


        recipeRepository.saveRecipe(id,recipe)

    }
}
