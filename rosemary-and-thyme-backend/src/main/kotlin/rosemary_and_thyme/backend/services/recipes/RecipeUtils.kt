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

/** Converts recipe database records into API data-transfer objects. */
object RecipeUtils {

    /** Extracts a Cloudinary public id from a delivery URL.
     * @param url the Cloudinary delivery URL.
     * @return the public id accepted by Cloudinary deletion requests.
     */
    fun extractPublicId(url: String): String =
        url.substringAfter("/upload/")
            .substringAfter("/")
            .substringBeforeLast(".")


    /** Builds a complete recipe DTO from its stored rows.
     * @param recipe the base recipe row.
     * @param ingredients the recipe ingredients.
     * @param instructions the recipe instructions.
     * @param instructionImages images grouped by instruction id.
     * @param heroImages hero images keyed by image id.
     * @param liked whether the requesting user liked the recipe.
     * @return the assembled recipe DTO.
     */
    fun buildRecipeDTO(
        recipe: RecipesRecord,
        ingredients: List<IngredientsRecord>,
        instructions: List<InstructionsRecord>,
        instructionImages: Map<UUID, List<Pair<UUID, String>>>,
        heroImages: Map<UUID, String>,
        liked: Boolean = false
    ): RecipeDTO {

        val imagesMap = mutableMapOf<UUID, RecipeImageDTO>()

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
            timesMade = recipe.timesMade.dbNotNull("timesMade"),
            liked = liked,
            portions = recipe.portions,
            cookTime = recipe.cookTime,
            difficulty = recipe.difficulty,
            sideNotes = recipe.sideNotes,
            images = imagesMap,
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
            instructions = instructions
                .associate { inst ->
                    val id = inst.id.dbNotNull("id")
                    id to InstructionDTO(
                        id = id,
                        text = inst.instruction,
                        images = instructionImages[inst.id]?.map { it.first } ?: emptyList()
                    )
                },
            ingredientsOrder = ingredients.mapNotNull { it.id },
            instructionsOrder = instructions.mapNotNull { it.id },
            heroImagesOrder = heroImages.keys.map { it },
            isPrivate = recipe.isPrivate.dbNotNull("isPrivate")
        )
    }
}
