package rosemary_and_thyme.backend.repositories.recipes

import rosemary_and_thyme.backend.jooqgenerated.public.tables.records.HeroImagesRecord
import rosemary_and_thyme.backend.jooqgenerated.public.tables.records.IngredientsRecord
import rosemary_and_thyme.backend.jooqgenerated.public.tables.records.InstructionImagesRecord
import rosemary_and_thyme.backend.jooqgenerated.public.tables.records.InstructionsRecord
import rosemary_and_thyme.backend.jooqgenerated.public.tables.records.RecipesRecord
import rosemary_and_thyme.backend.models.ResponsePayload
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID
import kotlin.collections.flatten
import kotlin.collections.mapNotNull
import kotlin.collections.sortedBy
import kotlin.let

/**
 * Data Classes that mirror types on the frontend
 *
 * JOOQ will always add nullable to columns that have default values because of that
 * we use !! operator to declare not null property that is guaranteed by default DDL keyword
 */

data class RecipeImageDTO(
    val id: UUID,
    val url: String?
)

data class IngredientDTO(
    val id: UUID,
    val name: String,
    val amount: BigDecimal,
    val measuringUnit: String
)

data class InstructionDTO(
    val id: UUID,
    val text: String,
    val images: List<UUID>
)

data class RecipeDTO(
    val id: UUID,
    val userId: UUID,
    val userName: String,
    val createDate: LocalDate,
    val name: String,
    val description: String,
    val likes: Long,
    val portions: Int,
    val cookTime: String,
    val difficulty: Int,
    val sideNotes: String?,
    val images: Map<UUID, RecipeImageDTO>,
    val ingredients: Map<UUID, IngredientDTO>,
    val instructions: Map<UUID, InstructionDTO>,
    val ingredientsOrder: List<UUID>,
    val instructionsOrder: List<UUID>,
    val heroImagesOrder: List<UUID>
): ResponsePayload

data class RecipeQueryResponseDTO(
   val message:String
): ResponsePayload


class RecipeUtils {

    /**
     * util for building RecipeDTO from a database rows
     */
    companion object {
        fun buildRecipeDTO(
            recipe: RecipesRecord,
            ingredients: List<IngredientsRecord>,
            instructions: List<InstructionsRecord>,
            instructionImages: Map<UUID, List<Pair<UUID,String>>>,
            //Change to recieve only List<UUID> representing those images
            heroImages: Map<UUID,String>
        ): RecipeDTO {

            val imagesMap = mutableMapOf<UUID, RecipeImageDTO>()

            /**
             * put all images in imagesMap
             */
            heroImages.entries.forEach { i -> imagesMap[i.key] = RecipeImageDTO(i.key,i.value) }
            instructionImages.values.flatten().forEach { i->imagesMap[i.first]= RecipeImageDTO(i.first,i.second) }

            return RecipeDTO(
                id=recipe.id!!,
                userId = recipe.userId,
                userName = recipe.userName,
                createDate = recipe.createdDate!!,
                name = recipe.name,
                description = recipe.description,
                likes = recipe.likes!!,
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
                        i.id!! to IngredientDTO(
                            id = i.id!!,
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
                        inst.id!! to InstructionDTO(
                            id = inst.id!!,
                            text = inst.instruction,
                            images = instructionImages[inst.id]?.map {it.first } ?: emptyList()
                        )
                    },
                /**
                 * Order by index to match frontend expectations
                 */
                ingredientsOrder = ingredients.mapNotNull { it.id },
                instructionsOrder = instructions.mapNotNull { it.id },
                heroImagesOrder = heroImages.keys.map { it }
            )
        }
    }

}

