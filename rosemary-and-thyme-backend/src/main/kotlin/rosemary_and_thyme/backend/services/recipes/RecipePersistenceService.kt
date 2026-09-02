package rosemary_and_thyme.backend.services.recipes

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rosemary_and_thyme.backend.jooqgenerated.app_auth.tables.records.UsersRecord
import rosemary_and_thyme.backend.models.recipes.RecipeWriteDTO
import rosemary_and_thyme.backend.repositories.recipes.RecipeRepository
import java.util.UUID

/**
 * Separate bean so Spring actually applies the transaction proxy to recipe writes.
 */
@Service
class RecipePersistenceService(private val recipeRepository: RecipeRepository) {
    @Transactional
    fun save(recipeId: UUID?, recipe: RecipeWriteDTO, user: UsersRecord): UUID {
        val persistedId = recipeRepository.upsertRecipeBase(recipeId, recipe, user)
        recipeRepository.syncIngredients(persistedId, recipe.ingredients, recipe.ingredientsOrder)
        recipeRepository.syncHeroImages(persistedId, recipe.images, recipe.heroImagesOrder)
        recipeRepository.syncInstructions(persistedId, recipe.instructions, recipe.instructionsOrder, recipe.images)
        return persistedId
    }

    @Transactional
    fun delete(recipeId: UUID) = recipeRepository.deleteRecipeAndImages(recipeId)
}
