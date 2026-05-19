package rosemary_and_thyme.backend.repositories.recipes

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import rosemary_and_thyme.backend.jooqgenerated.public.tables.records.HeroImagesRecord
import rosemary_and_thyme.backend.jooqgenerated.public.tables.records.IngredientsRecord
import rosemary_and_thyme.backend.jooqgenerated.public.tables.records.InstructionsRecord
import rosemary_and_thyme.backend.jooqgenerated.public.tables.records.RecipesRecord
import rosemary_and_thyme.backend.models.ApiResponse
import java.util.*

fun RecipeRepository.fetchRecipe(id: UUID): ApiResponse<RecipeDTO> = runBlocking {
    coroutineScope {
        val recipeDeferred: Deferred<RecipesRecord?> = async(Dispatchers.IO) { fetchRecipeBase(id) }
        val ingredientsDeferred: Deferred<List<IngredientsRecord>> = async(Dispatchers.IO) { fetchIngredients(id) }
        val instructionsDeferred: Deferred<List<InstructionsRecord>> = async(Dispatchers.IO) { fetchInstructions(id) }
        val heroImagesDeferred: Deferred<List<HeroImagesRecord>> = async(Dispatchers.IO) { fetchHeroImages(id) }

        val recipe = recipeDeferred.await() ?: throw NoSuchElementException("Recipe $id not found")
        val ingredients = ingredientsDeferred.await()
        val instructions = instructionsDeferred.await()
        val heroImages = heroImagesDeferred.await()

        val instructionImages = fetchInstructionImages(instructions.mapNotNull { it.id })

        ApiResponse(
            Utils.buildRecipeDTO(
                ingredients = ingredients,
                instructions = instructions,
                heroImages = heroImages,
                recipe = recipe,
                instructionImages = instructionImages
            ),
            error = null
        )
    }
}

suspend fun RecipeRepository.saveRecipe(recipe: RecipeDTO) = coroutineScope {
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

