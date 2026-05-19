package rosemary_and_thyme.backend.repositories.recipes

import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import rosemary_and_thyme.backend.jooqgenerated.public.tables.HeroImages.Companion.HERO_IMAGES
import rosemary_and_thyme.backend.jooqgenerated.public.tables.Ingredients.Companion.INGREDIENTS
import rosemary_and_thyme.backend.jooqgenerated.public.tables.InstructionImages.Companion.INSTRUCTION_IMAGES
import rosemary_and_thyme.backend.jooqgenerated.public.tables.Instructions.Companion.INSTRUCTIONS
import rosemary_and_thyme.backend.jooqgenerated.public.tables.Recipes.Companion.RECIPES
import rosemary_and_thyme.backend.jooqgenerated.public.tables.records.*
import java.util.*

@Repository
class RecipeRepository(val dsl: DSLContext) {


    fun RecipeRepository.saveRecipeBase(dto: RecipeDTO): UUID {
        val existingId = dsl.select(RECIPES.ID)
            .from(RECIPES)
            .where(RECIPES.ID.eq(dto.id))
            .fetchOne(RECIPES.ID)

        return if (existingId != null) {
            dsl.update(RECIPES)
                .set(RECIPES.NAME, dto.name)
                .set(RECIPES.DESCRIPTION, dto.description)
                .set(RECIPES.COOK_TIME, dto.cookTime)
                .set(RECIPES.DIFFICULTY, dto.difficulty)
                .set(RECIPES.PORTIONS, dto.portions)
                .set(RECIPES.SIDE_NOTES, dto.sideNotes)
                .where(RECIPES.ID.eq(dto.id))
                .execute()
            existingId // vrati postojeći ID
        } else {
            dsl.insertInto(RECIPES)
                .set(RECIPES.USER_ID, dto.userId)
                .set(RECIPES.USER_NAME, dto.userName)
                .set(RECIPES.NAME, dto.name)
                .set(RECIPES.DESCRIPTION, dto.description)
                .set(RECIPES.COOK_TIME, dto.cookTime)
                .set(RECIPES.DIFFICULTY, dto.difficulty)
                .set(RECIPES.PORTIONS, dto.portions)
                .set(RECIPES.SIDE_NOTES, dto.sideNotes)
                .set(RECIPES.LIKES, 0L)
                .returningResult(RECIPES.ID)
                .fetchOne()
                ?.get(RECIPES.ID) ?: throw IllegalStateException("Failed to insert recipe")
            // baza generira novi ID
        }
    }

    fun RecipeRepository.saveIngredients(
        recipeId: UUID,
        ingredients: Map<UUID, IngredientDTO>,
        order: List<UUID>
    ) {
        val existingIds = dsl.select(INGREDIENTS.ID)
            .from(INGREDIENTS)
            .where(INGREDIENTS.RECIPE_ID.eq(recipeId))
            .fetchSet(INGREDIENTS.ID)

        val (toUpdate, toInsert) = ingredients.values.partition { it.id in existingIds }

        // batch update
        if (toUpdate.isNotEmpty()) {
            dsl.batch(
                toUpdate.map { ing ->
                    val index = order.indexOf(ing.id)
                    dsl.update(INGREDIENTS)
                        .set(INGREDIENTS.NAME, ing.name)
                        .set(INGREDIENTS.AMOUNT, ing.amount)
                        .set(INGREDIENTS.MEASURING_UNIT, ing.measuringUnit)
                        .set(INGREDIENTS.INDEX, index)
                        .where(INGREDIENTS.ID.eq(ing.id))
                        .and(INGREDIENTS.RECIPE_ID.eq(recipeId))
                }
            ).execute()
        }

        // batch insert, baza generira ID
        if (toInsert.isNotEmpty()) {
            dsl.batch(
                toInsert.map { ing ->
                    val index = order.indexOf(ing.id) // temp ID je još uvijek u order listi
                    dsl.insertInto(INGREDIENTS)
                        .set(INGREDIENTS.RECIPE_ID, recipeId)
                        .set(INGREDIENTS.NAME, ing.name)
                        .set(INGREDIENTS.AMOUNT, ing.amount)
                        .set(INGREDIENTS.MEASURING_UNIT, ing.measuringUnit)
                        .set(INGREDIENTS.INDEX, index)
                    // ne šalješ ID → baza generira
                }
            ).execute()
        }

        // obrisi koji više ne postoje
        val toDelete = existingIds - ingredients.keys
        if (toDelete.isNotEmpty()) {
            dsl.deleteFrom(INGREDIENTS)
                .where(INGREDIENTS.RECIPE_ID.eq(recipeId))
                .and(INGREDIENTS.ID.`in`(toDelete))
                .execute()
        }
    }

    fun RecipeRepository.saveInstructions(
        recipeId: UUID,
        instructions: Map<UUID, InstructionDTO>,
        order: List<UUID>,
        images: Map<UUID, RecipeImageDTO>
    ) {
        val existingIds = dsl.select(INSTRUCTIONS.ID)
            .from(INSTRUCTIONS)
            .where(INSTRUCTIONS.RECIPE_ID.eq(recipeId))
            .fetchSet(INSTRUCTIONS.ID)

        val (toUpdate, toInsert) = instructions.values.partition { it.id in existingIds }

        if (toUpdate.isNotEmpty()) {
            dsl.batch(
                toUpdate.map { inst ->
                    val index = order.indexOf(inst.id)
                    dsl.update(INSTRUCTIONS)
                        .set(INSTRUCTIONS.INSTRUCTION, inst.text)
                        .set(INSTRUCTIONS.INDEX, index)
                        .where(INSTRUCTIONS.ID.eq(inst.id))
                        .and(INSTRUCTIONS.RECIPE_ID.eq(recipeId))
                }
            ).execute()
        }

        // za insert trebamo prave ID-ove koje vrati baza da bismo mogli saveati instruction images
        if (toInsert.isNotEmpty()) {
            toInsert.forEach { inst ->
                val index = order.indexOf(inst.id)
                val newId = dsl.insertInto(INSTRUCTIONS)
                    .set(INSTRUCTIONS.RECIPE_ID, recipeId)
                    .set(INSTRUCTIONS.INSTRUCTION, inst.text)
                    .set(INSTRUCTIONS.INDEX, index)
                    .returningResult(INSTRUCTIONS.ID)
                    .fetchOne()
                    ?.get(INSTRUCTIONS.ID) ?: throw IllegalStateException("Failed to insert instruction")

                saveInstructionImages(newId, inst.images, images)
            }
        }

        // za postojeće updateaj instruction images
        toUpdate.forEach { inst ->
            saveInstructionImages(inst.id, inst.images, images)
        }

        val toDelete = existingIds - instructions.keys
        if (toDelete.isNotEmpty()) {
            dsl.deleteFrom(INSTRUCTIONS)
                .where(INSTRUCTIONS.RECIPE_ID.eq(recipeId))
                .and(INSTRUCTIONS.ID.`in`(toDelete))
                .execute() // CASCADE obriše instruction_images
        }
    }

    fun RecipeRepository.saveHeroImages(
        recipeId: UUID,
        images: Map<UUID, RecipeImageDTO>,
        order: List<UUID>
    ) {
        val existingIds = dsl.select(HERO_IMAGES.ID)
            .from(HERO_IMAGES)
            .where(HERO_IMAGES.RECIPE_ID.eq(recipeId))
            .fetchSet(HERO_IMAGES.ID)

        val heroImages = order.mapNotNull { images[it] }
        val (toUpdate, toInsert) = heroImages.partition { it.id in existingIds }

        if (toUpdate.isNotEmpty()) {
            dsl.batch(
                toUpdate.map { img ->
                    val index = order.indexOf(img.id)
                    dsl.update(HERO_IMAGES)
                        .set(HERO_IMAGES.URL, img.url)
                        .set(HERO_IMAGES.INDEX, index)
                        .where(HERO_IMAGES.ID.eq(img.id))
                        .and(HERO_IMAGES.RECIPE_ID.eq(recipeId))
                }
            ).execute()
        }

        if (toInsert.isNotEmpty()) {
            dsl.batch(
                toInsert.map { img ->
                    val index = order.indexOf(img.id)
                    dsl.insertInto(HERO_IMAGES)
                        .set(HERO_IMAGES.RECIPE_ID, recipeId)
                        .set(HERO_IMAGES.URL, img.url)
                        .set(HERO_IMAGES.INDEX, index)
                    // ne šalješ ID → baza generira
                }
            ).execute()
        }

        val toDelete = existingIds - order.toSet()
        if (toDelete.isNotEmpty()) {
            dsl.deleteFrom(HERO_IMAGES)
                .where(HERO_IMAGES.RECIPE_ID.eq(recipeId))
                .and(HERO_IMAGES.ID.`in`(toDelete))
                .execute()
        }
    }

    fun RecipeRepository.saveInstructionImages(
        instructionId: UUID,
        imageIds: List<UUID>,
        images: Map<UUID, RecipeImageDTO>
    ) {
        val existingIds = dsl.select(INSTRUCTION_IMAGES.ID)
            .from(INSTRUCTION_IMAGES)
            .where(INSTRUCTION_IMAGES.INSTRUCTION_ID.eq(instructionId))
            .fetchSet(INSTRUCTION_IMAGES.ID)

        val instructionImages = imageIds.mapNotNull { images[it] }
        val (toUpdate, toInsert) = instructionImages.partition { it.id in existingIds }

        if (toUpdate.isNotEmpty()) {
            dsl.batch(
                toUpdate.map { img ->
                    val index = imageIds.indexOf(img.id)
                    dsl.update(INSTRUCTION_IMAGES)
                        .set(INSTRUCTION_IMAGES.URL, img.url)
                        .set(INSTRUCTION_IMAGES.INDEX, index)
                        .where(INSTRUCTION_IMAGES.ID.eq(img.id))
                        .and(INSTRUCTION_IMAGES.INSTRUCTION_ID.eq(instructionId))
                }
            ).execute()
        }

        if (toInsert.isNotEmpty()) {
            dsl.batch(
                toInsert.map { img ->
                    val index = imageIds.indexOf(img.id)
                    dsl.insertInto(INSTRUCTION_IMAGES)
                        .set(INSTRUCTION_IMAGES.INSTRUCTION_ID, instructionId)
                        .set(INSTRUCTION_IMAGES.URL, img.url)
                        .set(INSTRUCTION_IMAGES.INDEX, index)
                    // ne šalješ ID → baza generira
                }
            ).execute()
        }

        val toDelete = existingIds - imageIds.toSet()
        if (toDelete.isNotEmpty()) {
            dsl.deleteFrom(INSTRUCTION_IMAGES)
                .where(INSTRUCTION_IMAGES.INSTRUCTION_ID.eq(instructionId))
                .and(INSTRUCTION_IMAGES.ID.`in`(toDelete))
                .execute()
        }
    }


    fun fetchRecipeBase(recipeId: UUID): RecipesRecord? =
        dsl.selectFrom(RECIPES)
            .where(RECIPES.ID.eq(recipeId))
            .fetchOneInto(RecipesRecord::class.java)

    fun fetchIngredients(recipeId: UUID): List<IngredientsRecord> =
        dsl.selectFrom(INGREDIENTS)
            .where(INGREDIENTS.RECIPE_ID.eq(recipeId))
            .orderBy(INGREDIENTS.INDEX)
            .fetchInto(IngredientsRecord::class.java)

    fun fetchInstructions(recipeId: UUID): List<InstructionsRecord> =
        dsl.selectFrom(INSTRUCTIONS)
            .where(INSTRUCTIONS.RECIPE_ID.eq(recipeId))
            .orderBy(INSTRUCTIONS.INDEX)
            .fetchInto(InstructionsRecord::class.java)

    fun fetchHeroImages(recipeId: UUID): List<HeroImagesRecord> =
        dsl.selectFrom(HERO_IMAGES)
            .where(HERO_IMAGES.RECIPE_ID.eq(recipeId))
            .orderBy(HERO_IMAGES.INDEX)
            .fetchInto(HeroImagesRecord::class.java)

    fun fetchInstructionImages(instructionIds: List<UUID>): Map<UUID, List<InstructionImagesRecord>> =
        dsl.selectFrom(INSTRUCTION_IMAGES)
            .where(INSTRUCTION_IMAGES.INSTRUCTION_ID.`in`(instructionIds))
            .orderBy(INSTRUCTION_IMAGES.INDEX)
            .fetchInto(InstructionImagesRecord::class.java)
            .groupBy { it.instructionId }

    fun fetchHeroImageIds(recipeId: UUID): Set<UUID> =
        dsl.select(HERO_IMAGES.ID)
            .from(HERO_IMAGES)
            .where(HERO_IMAGES.RECIPE_ID.eq(recipeId))
            .fetchSet(HERO_IMAGES.ID)
            .filterNotNull()
            .toSet()

    fun fetchAllInstructionImageIds(recipeId: UUID): Set<UUID> =
        dsl.select(INSTRUCTION_IMAGES.ID)
            .from(INSTRUCTION_IMAGES)
            .join(INSTRUCTIONS).on(INSTRUCTION_IMAGES.INSTRUCTION_ID.eq(INSTRUCTIONS.ID))
            .where(INSTRUCTIONS.RECIPE_ID.eq(recipeId))
            .fetchSet(INSTRUCTION_IMAGES.ID)
            .filterNotNull()
            .toSet()
}