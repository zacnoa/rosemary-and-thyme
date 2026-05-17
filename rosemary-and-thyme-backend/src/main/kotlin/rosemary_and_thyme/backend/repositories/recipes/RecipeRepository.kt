package rosemary_and_thyme.backend.repositories.recipes

import kotlinx.coroutines.*
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import rosemary_and_thyme.backend.jooqgenerated.public.tables.HeroImages.Companion.HERO_IMAGES
import rosemary_and_thyme.backend.jooqgenerated.public.tables.Ingredients.Companion.INGREDIENTS
import rosemary_and_thyme.backend.jooqgenerated.public.tables.InstructionImages.Companion.INSTRUCTION_IMAGES
import rosemary_and_thyme.backend.jooqgenerated.public.tables.Instructions.Companion.INSTRUCTIONS
import rosemary_and_thyme.backend.jooqgenerated.public.tables.Recipes.Companion.RECIPES
import rosemary_and_thyme.backend.jooqgenerated.public.tables.records.HeroImagesRecord
import rosemary_and_thyme.backend.jooqgenerated.public.tables.records.IngredientsRecord
import rosemary_and_thyme.backend.jooqgenerated.public.tables.records.InstructionImagesRecord
import rosemary_and_thyme.backend.jooqgenerated.public.tables.records.InstructionsRecord
import rosemary_and_thyme.backend.jooqgenerated.public.tables.records.RecipesRecord
import rosemary_and_thyme.backend.models.ApiResponse
import java.util.*

@Repository
 class RecipeRepository(val dsl: DSLContext) {

    fun fetchRecipe(id: UUID): ApiResponse<RecipeDTO> = runBlocking {
        coroutineScope {
            val recipeDeferred: Deferred<RecipesRecord?> = async(Dispatchers.IO) { fetchRecipeBase(id) }
            val ingredientsDeferred: Deferred<List<IngredientsRecord>> = async(Dispatchers.IO) { fetchIngredients(id) }
            val instructionsDeferred: Deferred<List<InstructionsRecord>> =
                async(Dispatchers.IO) { fetchInstructions(id) }
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
                error=null
                )
        }
    }
    fun saveRecipe(id:UUID, recipe: RecipeDTO) {
   }

}