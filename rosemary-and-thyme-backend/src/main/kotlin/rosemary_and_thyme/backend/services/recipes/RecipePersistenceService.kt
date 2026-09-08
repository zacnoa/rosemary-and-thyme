package rosemary_and_thyme.backend.services.recipes

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rosemary_and_thyme.backend.jooqgenerated.app_auth.tables.records.UsersRecord
import rosemary_and_thyme.backend.models.recipes.RecipeWriteDTO
import rosemary_and_thyme.backend.repositories.recipes.RecipeRepository
import java.util.UUID

/** Persists complete recipe changes in one database transaction. */
@Service
class RecipePersistenceService(private val recipeRepository: RecipeRepository) {
    /** Synchronizes every stored part of a recipe.
     * @param recipeId the existing recipe id, or `null` for a new recipe.
     * @param recipe the complete desired recipe state.
     * @param user the owner used when inserting a new recipe.
     * @return the persisted recipe id.
     */
    @Transactional
    fun save(recipeId: UUID?, recipe: RecipeWriteDTO, user: UsersRecord): UUID {
        val persistedId = recipeRepository.upsertRecipeBase(recipeId, recipe, user)
        recipeRepository.syncIngredients(persistedId, recipe.ingredients, recipe.ingredientsOrder)
        recipeRepository.syncHeroImages(persistedId, recipe.images, recipe.heroImagesOrder)
        recipeRepository.syncInstructions(persistedId, recipe.instructions, recipe.instructionsOrder, recipe.images)
        return persistedId
    }

    /** Deletes a recipe and its database image records.
     * @param recipeId the recipe to delete.
     */
    @Transactional
    fun delete(recipeId: UUID) = recipeRepository.deleteRecipeAndImages(recipeId)
}
