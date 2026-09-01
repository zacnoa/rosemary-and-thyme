package rosemary_and_thyme.backend.services.recipes

import rosemary_and_thyme.backend.jooqgenerated.public.tables.records.IngredientsRecord
import rosemary_and_thyme.backend.jooqgenerated.public.tables.records.InstructionsRecord
import rosemary_and_thyme.backend.jooqgenerated.public.tables.records.RecipesRecord
import rosemary_and_thyme.backend.models.recipes.IngredientDTO
import rosemary_and_thyme.backend.models.recipes.InstructionDTO
import rosemary_and_thyme.backend.models.recipes.RecipeDTO
import rosemary_and_thyme.backend.models.recipes.RecipeImageDTO
import rosemary_and_thyme.backend.repositories.dbNotNull
import java.util.UUID

/**
 * Assembly helpers for turning the several jOOQ records a recipe is split across
 * (see [rosemary_and_thyme.backend.repositories.recipes.RecipeRepository]) into the
 * single [RecipeDTO] shape the frontend expects, and back.
 */
object RecipeUtils {

    /**
     * Extracts the Cloudinary public id embedded in a Cloudinary delivery URL, e.g.
     * `.../upload/v1699999999/Home/Recipes/abc123.jpg` -> `Home/Recipes/abc123`.
     * Needed because Cloudinary's destroy (delete) API takes the public id, not the URL -
     * see the upload/delete calls in [RecipeService.saveRecipe].
     *
     * @param url a Cloudinary secure delivery URL
     * @return the public id to pass to `cloudinary.uploader().destroy(...)`
     */
    fun extractPublicId(url: String): String =
        url.substringAfter("/upload/")
            .substringAfter("/")
            .substringBeforeLast(".")


    /**
     * Builds the frontend-facing [RecipeDTO] out of the separate row sets a recipe is
     * stored across. Called after [RecipeService.findRecipeById] fetches those row sets
     * in parallel, so this itself runs synchronously once all of them are in hand.
     *
     * @param recipe the recipe's base row (name, description, portions, ...)
     * @param ingredients the recipe's ingredient rows
     * @param instructions the recipe's instruction step rows
     * @param instructionImages images per instruction id, as (imageId, url) pairs
     * @param heroImages hero images as imageId -> url. TODO: narrow this to just the
     * ordered list of image ids the caller actually needs (see the inline TODO in
     * [rosemary_and_thyme.backend.repositories.recipes.RecipeRepository.selectHeroImages] call sites).
     * @param liked whether the requesting caller has liked this recipe - `false` by
     * default so callers that don't care about like state (e.g. after a save, where
     * the caller is always the recipe's own owner and thus can never have liked it)
     * don't need to pass anything
     * @return the assembled [RecipeDTO], ready to serialize back to the client
     */
    fun buildRecipeDTO(
        recipe: RecipesRecord,
        ingredients: List<IngredientsRecord>,
        instructions: List<InstructionsRecord>,
        instructionImages: Map<UUID, List<Pair<UUID, String>>>,
        //Change to receive only List<UUID> representing those images
        heroImages: Map<UUID, String>,
        liked: Boolean = false
    ): RecipeDTO {

        val imagesMap = mutableMapOf<UUID, RecipeImageDTO>()

        /**
         * put all images in imagesMap
         */
        heroImages.entries.forEach { i -> imagesMap[i.key] = RecipeImageDTO(i.key, i.value) }
        instructionImages.values.flatten().forEach { i -> imagesMap[i.first] = RecipeImageDTO(i.first, i.second) }

        return RecipeDTO(
            id = recipe.id.dbNotNull("id"),
            userId = recipe.userId,
            userName = recipe.userName,
            createDate = recipe.createdDate.dbNotNull("createdDate"),
            name = recipe.name,
            description = recipe.description,
            likes = recipe.likes.dbNotNull("likes"),
            liked = liked,
            portions = recipe.portions,
            cookTime = recipe.cookTime,
            difficulty = recipe.difficulty,
            sideNotes = recipe.sideNotes,
            images = imagesMap,
            /**
             * Map ingredients to their id and value
             */
            ingredients = ingredients
                .associate { i ->
                    val id = i.id.dbNotNull("id")
                    id to IngredientDTO(
                        id = id,
                        name = i.name,
                        amount = i.amount,
                        measuringUnit = i.measuringUnit
                    )
                },
            /**
             * Map instructions to their id and value
             */
            instructions = instructions
                .associate { inst ->
                    val id = inst.id.dbNotNull("id")
                    id to InstructionDTO(
                        id = id,
                        text = inst.instruction,
                        images = instructionImages[inst.id]?.map { it.first } ?: emptyList()
                    )
                },
            /**
             * Order by index to match frontend expectations
             */
            ingredientsOrder = ingredients.mapNotNull { it.id },
            instructionsOrder = instructions.mapNotNull { it.id },
            heroImagesOrder = heroImages.keys.map { it },
            isPrivate = recipe.isPrivate.dbNotNull("isPrivate")
        )
    }
}
