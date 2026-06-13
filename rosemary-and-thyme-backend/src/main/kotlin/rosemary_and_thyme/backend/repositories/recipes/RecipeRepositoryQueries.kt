package rosemary_and_thyme.backend.repositories.recipes

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.springframework.transaction.annotation.Transactional
import rosemary_and_thyme.backend.jooqgenerated.public.tables.records.HeroImagesRecord
import rosemary_and_thyme.backend.jooqgenerated.public.tables.records.IngredientsRecord
import rosemary_and_thyme.backend.jooqgenerated.public.tables.records.InstructionsRecord
import rosemary_and_thyme.backend.jooqgenerated.public.tables.records.RecipesRecord
import rosemary_and_thyme.backend.models.ApiResponse
import java.util.*


/**
 *
 * Extension functions for RecipeRepository which serve as async orchestrators of the functions
 * defined in RecipeRepository
 *
 * each function uses coroutineScope to handle multiple threads and uses Dispatchers.IO for running blocking JOOQ queries.
 */


suspend fun RecipeRepository.fetchRecipe(id: UUID): ApiResponse<RecipeDTO> = coroutineScope {
    /**
     * 4 async IO threads for fetching individual parts of a recipe
     */
    val recipeDeferred: Deferred<RecipesRecord?> = async(Dispatchers.IO) { fetchRecipeBase(id) }
    val ingredientsDeferred: Deferred<List<IngredientsRecord>> = async(Dispatchers.IO) { fetchIngredients(id) }
    val instructionsDeferred: Deferred<List<InstructionsRecord>> = async(Dispatchers.IO) { fetchInstructions(id) }
    val heroImagesDeferred: Deferred<Map<UUID,String>> = async(Dispatchers.IO) { fetchHeroImages(id) }

    /**
     * Waits for the async calls
     *
     * recipeDeffered throws NoSuchElementException when fetchRecipeBase returns null(meaning no recipe by that id)
     */
    val recipe = recipeDeferred.await() ?: throw NoSuchElementException("The recipe you are looking for does not exist")
    val ingredients = ingredientsDeferred.await()
    val instructions = instructionsDeferred.await()
    val heroImages = heroImagesDeferred.await()

    /**
     * We fetchInstructionImages last because it needs to have access to instructionIds
     */
    val instructionImages = fetchInstructionImages(instructions.mapNotNull { it.id })


    ApiResponse(
        RecipeUtils.buildRecipeDTO(
            recipe = recipe,
            ingredients = ingredients,
            instructions = instructions,
            heroImages = heroImages,
            instructionImages = instructionImages
        ),
    )
}

@Transactional
suspend fun RecipeRepository.saveRecipe(recipe: RecipeDTO) = coroutineScope {
    /**
     * This call is not async because before we call any other query we need the real id of the recipe
     */
    val recipeId = saveRecipeBase(recipe)

    val saveIngredientsJob = async(Dispatchers.IO) {
        saveIngredients(recipeId, recipe.ingredients, recipe.ingredientsOrder)
    }
    val saveHeroImagesJob = async(Dispatchers.IO) {
        saveHeroImages(recipeId, recipe.images, recipe.heroImagesOrder)
    }
    val saveInstructionsJob = async(Dispatchers.IO) {
        saveInstructions(recipeId, recipe.instructions, recipe.instructionsOrder, recipe.images)
    }
    saveIngredientsJob.await()
    saveHeroImagesJob.await()
    saveInstructionsJob.await()
}

