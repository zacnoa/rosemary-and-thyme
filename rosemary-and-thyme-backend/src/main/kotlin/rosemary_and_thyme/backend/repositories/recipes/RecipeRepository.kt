package rosemary_and_thyme.backend.repositories.recipes

import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.jooq.impl.SQLDataType
import org.springframework.stereotype.Repository
import rosemary_and_thyme.backend.jooqgenerated.auth.tables.records.UsersRecord
import rosemary_and_thyme.backend.jooqgenerated.public.tables.HeroImages.Companion.HERO_IMAGES
import rosemary_and_thyme.backend.jooqgenerated.public.tables.Ingredients.Companion.INGREDIENTS
import rosemary_and_thyme.backend.jooqgenerated.public.tables.InstructionImages.Companion.INSTRUCTION_IMAGES
import rosemary_and_thyme.backend.jooqgenerated.public.tables.Instructions.Companion.INSTRUCTIONS
import rosemary_and_thyme.backend.jooqgenerated.public.tables.Recipes.Companion.RECIPES
import rosemary_and_thyme.backend.jooqgenerated.public.tables.records.*
import rosemary_and_thyme.backend.jooqgenerated.public.tables.references.IMAGES
import rosemary_and_thyme.backend.services.recipes.IngredientDTO
import rosemary_and_thyme.backend.services.recipes.InstructionDTO
import rosemary_and_thyme.backend.services.recipes.RecipeDTO
import rosemary_and_thyme.backend.services.recipes.RecipeImageDTO
import java.util.*
import kotlin.collections.partition


/**
 * Repository layer
 *
 * Contains all database queries for the specific domain.
 * The member functions are not declared as suspend because
 * JOOQ functions are blocking.
 * These functions should be called by a Dispatchers.IO if parallelism is needed
 *
 */

@Repository
class RecipeRepository(val dsl: DSLContext) {

    fun saveRecipeBase(dto: RecipeDTO, user: UsersRecord): UUID {
        /**
         * check if recipe with given id exists
         */
        val existingId = dsl.select(RECIPES.ID)
            .from(RECIPES)
            .where(RECIPES.ID.eq(dto.id))
            .fetchOne(RECIPES.ID)

        /**
         *  if there is a recipe update existing row and return id
         */
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
            existingId
        }
        /**
         * if there is no recipe by that id insert new recipe and return the new id
         *
         * id, user_id and user_name are never taken from the client-submitted dto:
         * the id is DB-generated (gen_random_uuid()) and the owner is always the
         * authenticated principal, so the client cannot spoof another user's recipe
         */
        else {
            dsl.insertInto(RECIPES)
                .set(RECIPES.USER_ID, user.id)
                .set(RECIPES.USER_NAME, user.username)
                .set(RECIPES.NAME, dto.name)
                .set(RECIPES.DESCRIPTION, dto.description)
                .set(RECIPES.COOK_TIME, dto.cookTime)
                .set(RECIPES.DIFFICULTY, dto.difficulty)
                .set(RECIPES.PORTIONS, dto.portions)
                .set(RECIPES.SIDE_NOTES, dto.sideNotes)
                .set(RECIPES.LIKES, 0)
                .returningResult(RECIPES.ID)
                .fetchOne()
                ?.get(RECIPES.ID) ?: throw IllegalStateException("Failed to insert recipe")
        }
    }

    fun selectAllRecipes(userId: UUID): List<Pair<UUID, String>> =

        dsl.select(RECIPES.ID,RECIPES.NAME).from(RECIPES)
            .where(RECIPES.USER_ID.eq(userId))
            .limit(100)
            .fetch{record -> Pair(record[RECIPES.ID]!!,record[RECIPES.NAME]!!)}


    fun saveIngredients(
        recipeId: UUID,
        ingredients: Map<UUID, IngredientDTO>,
        order: List<UUID>
    ) {
        /**
         * fetch existing ids for checking with client state
         */
        val existingIds = dsl.select(INGREDIENTS.ID)
            .from(INGREDIENTS)
            .where(INGREDIENTS.RECIPE_ID.eq(recipeId))
            .fetchSet(INGREDIENTS.ID)

        val (toUpdate, toInsert) = ingredients.values.partition { it.id in existingIds }

        /**
         * update all ingredients
         * we use batch to avoid unnecessary calls to the database
         */
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

        /**
         * insert new recipes
         * we don't send ingredient id because we want the database to be the sole UUID generator
         */
        if (toInsert.isNotEmpty()) {
            val result = dsl.batch(
                toInsert.map { ing ->
                    val index = order.indexOf(ing.id)
                    dsl.insertInto(INGREDIENTS)
                        .set(INGREDIENTS.RECIPE_ID, recipeId)
                        .set(INGREDIENTS.NAME, ing.name)
                        .set(INGREDIENTS.AMOUNT, ing.amount)
                        .set(INGREDIENTS.MEASURING_UNIT, ing.measuringUnit)
                        .set(INGREDIENTS.INDEX, index)
                }
            ).execute()
            check(result.any { it == 1 }) { "Error while inserting ingredients" }
        }

        /**
         * delete ingredients
         */
        val toDelete = existingIds - ingredients.keys
        if (toDelete.isNotEmpty()) {
            dsl.deleteFrom(INGREDIENTS)
                .where(INGREDIENTS.RECIPE_ID.eq(recipeId))
                .and(INGREDIENTS.ID.`in`(toDelete))
                .execute()
        }
    }

    fun saveInstructions(
        recipeId: UUID,
        instructions: Map<UUID, InstructionDTO>,
        order: List<UUID>,
        images: Map<UUID, RecipeImageDTO>
    ) {

        /**
         * fetch existing id for cheking
         */
        val existingIds = dsl.select(INSTRUCTIONS.ID)
            .from(INSTRUCTIONS)
            .where(INSTRUCTIONS.RECIPE_ID.eq(recipeId))
            .fetchSet(INSTRUCTIONS.ID)


        val (toUpdate, toInsert) = instructions.values.partition { it.id in existingIds }

        /**
         * update existing instructions
         */
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

        /**
         * insert new instructions
         */
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

        /**
         * update image for existing images
         */
        toUpdate.forEach { inst ->
            saveInstructionImages(inst.id, inst.images, images)
        }

        /**
         * cascade setting on instruction Images ensures they are deleted after deleting corresponding instruction
         */
        val toDelete = existingIds - instructions.keys
        if (toDelete.isNotEmpty()) {
            dsl.deleteFrom(INSTRUCTIONS)
                .where(INSTRUCTIONS.RECIPE_ID.eq(recipeId))
                .and(INSTRUCTIONS.ID.`in`(toDelete))
                .execute()
        }
    }

    fun saveHeroImages(
        recipeId: UUID,
        images: Map<UUID, RecipeImageDTO>,
        order: List<UUID>
    ) {
        val existingIds = fetchHeroImageIds(recipeId)
        val heroImages = order.mapNotNull { images[it] }
        val (toUpdate, toInsert) = heroImages.partition { it.id in existingIds }

        // UPDATE - dvije tablice
        if (toUpdate.isNotEmpty()) {
            // Update index u hero_images
            dsl.batch(
                toUpdate.map { img ->
                    val index = order.indexOf(img.id)
                    dsl.update(HERO_IMAGES)
                        .set(HERO_IMAGES.INDEX, index)
                        .where(HERO_IMAGES.IMAGE_ID.eq(img.id))
                        .and(HERO_IMAGES.RECIPE_ID.eq(recipeId))
                }
            ).execute()
            // Update url u images
            dsl.batch(
                toUpdate.map { img ->
                    dsl.update(IMAGES)
                        .set(IMAGES.URL, img.url)
                        .where(IMAGES.ID.eq(img.id))
                }
            ).execute()
        }

        // INSERT - dvije tablice
        if (toInsert.isNotEmpty()) {
            toInsert.forEachIndexed { _, img ->
                val index = order.indexOf(img.id)
                // 1. Insert u images, dobijes novi image_id
                val imageId = dsl.insertInto(IMAGES)
                    .set(IMAGES.URL, img.url)
                    .returningResult(IMAGES.ID)
                    .fetchOne()!!.value1()
                // 2. Insert u hero_images s tim image_id
                dsl.insertInto(HERO_IMAGES)
                    .set(HERO_IMAGES.IMAGE_ID, imageId)
                    .set(HERO_IMAGES.RECIPE_ID, recipeId)
                    .set(HERO_IMAGES.INDEX, index)
                    .execute()
            }
        }

        // DELETE - CASCADE ce obrisati hero_images automatski
        val toDelete = existingIds - order.toSet()
        if (toDelete.isNotEmpty()) {
            dsl.deleteFrom(IMAGES)
                .where(IMAGES.ID.`in`(toDelete))
                .execute()
        }
    }

    fun saveInstructionImages(
        instructionId: UUID,
        imageIds: List<UUID>,
        images: Map<UUID, RecipeImageDTO>
    ) {
        /**
         * Here we cannot reuse fetchInstructionImages because the type Map<UUID, List<String>> loses the id information of individual images
         */
        val existingIds = fetchInstructionImageIds(instructionId)
        val instructionImages = imageIds.mapNotNull { images[it] }
        val (toUpdate, toInsert) = instructionImages.partition { it.id in existingIds }

        // UPDATE
        if (toUpdate.isNotEmpty()) {
            // Update index u instruction_images
            dsl.batch(
                toUpdate.map { img ->
                    val index = imageIds.indexOf(img.id)
                    dsl.update(INSTRUCTION_IMAGES)
                        .set(INSTRUCTION_IMAGES.INDEX, index)
                        .where(INSTRUCTION_IMAGES.IMAGE_ID.eq(img.id))
                        .and(INSTRUCTION_IMAGES.INSTRUCTION_ID.eq(instructionId))
                }
            ).execute()
            // Update url u images
            dsl.batch(
                toUpdate.map { img ->
                    dsl.update(IMAGES)
                        .set(IMAGES.URL, img.url)
                        .where(IMAGES.ID.eq(img.id))
                }
            ).execute()
        }

        // INSERT
        if (toInsert.isNotEmpty()) {
            toInsert.forEach { img ->
                val index = imageIds.indexOf(img.id)
                // 1. Insert u images
                val imageId = dsl.insertInto(IMAGES)
                    .set(IMAGES.URL, img.url)
                    .returningResult(IMAGES.ID)
                    .fetchOne()!!.value1()
                // 2. Insert u instruction_images s tim image_id
                dsl.insertInto(INSTRUCTION_IMAGES)
                    .set(INSTRUCTION_IMAGES.IMAGE_ID, imageId)
                    .set(INSTRUCTION_IMAGES.INSTRUCTION_ID, instructionId)
                    .set(INSTRUCTION_IMAGES.INDEX, index)
                    .execute()
            }
        }

        // DELETE - CASCADE briše instruction_images automatski
        val toDelete = existingIds - imageIds.toSet()
        if (toDelete.isNotEmpty()) {
            dsl.deleteFrom(IMAGES)
                .where(IMAGES.ID.`in`(toDelete))
                .execute()
        }
    }

    /**
     * Queries for fetching  recipe by id
     */
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

    fun fetchHeroImages(recipeId: UUID): Map<UUID, String> =
        dsl.select(HERO_IMAGES.IMAGE_ID, IMAGES.URL)
            .from(HERO_IMAGES)
            .join(IMAGES)
            .on(HERO_IMAGES.IMAGE_ID.eq(IMAGES.ID))
            .where(HERO_IMAGES.RECIPE_ID.eq(recipeId))
            .orderBy(HERO_IMAGES.INDEX)
            .fetch()
            .associate { it[HERO_IMAGES.IMAGE_ID]!! to it[IMAGES.URL]!! }

    fun fetchInstructionImages(instructionIds: List<UUID>): Map<UUID, List<Pair<UUID, String>>> =
        dsl.select(INSTRUCTION_IMAGES.INSTRUCTION_ID, INSTRUCTION_IMAGES.IMAGE_ID, IMAGES.URL)
            .from(INSTRUCTION_IMAGES)
            .join(IMAGES).on(INSTRUCTION_IMAGES.IMAGE_ID.eq(IMAGES.ID))
            .where(INSTRUCTION_IMAGES.INSTRUCTION_ID.`in`(instructionIds))
            .orderBy(INSTRUCTION_IMAGES.INDEX)
            .fetch()
            .groupBy(
                { it[INSTRUCTION_IMAGES.INSTRUCTION_ID]!! },
                { it[INSTRUCTION_IMAGES.IMAGE_ID]!! to it[IMAGES.URL]!! }
            )

    /**
     * May cause problems with different combination of columns
     */
    fun fetchAllImageIds(recipeId: UUID): List<UUID> {
        val heroImage = dsl.select(HERO_IMAGES.IMAGE_ID)
            .from(HERO_IMAGES)
            .join(IMAGES)
            .on(HERO_IMAGES.IMAGE_ID.eq(IMAGES.ID))
            .where((HERO_IMAGES.RECIPE_ID).eq(recipeId))

        val instructionImages = dsl.select(INSTRUCTION_IMAGES.IMAGE_ID)
            .from(INSTRUCTION_IMAGES)
            .join(INSTRUCTIONS).on(INSTRUCTION_IMAGES.INSTRUCTION_ID.eq(INSTRUCTIONS.ID))
            .join(IMAGES).on(INSTRUCTION_IMAGES.IMAGE_ID.eq(IMAGES.ID))
            .where(INSTRUCTIONS.RECIPE_ID.eq(recipeId))

        return heroImage.union(instructionImages)
            .fetch(HERO_IMAGES.IMAGE_ID)
            .filterNotNull()

    }

    fun fetchHeroImageIds(recipeId: UUID): Set<UUID> =
        dsl.select(HERO_IMAGES.IMAGE_ID)
            .from(HERO_IMAGES)
            .where(HERO_IMAGES.RECIPE_ID.eq(recipeId))
            .fetch(HERO_IMAGES.IMAGE_ID)
            .filterNotNull()
            .toSet()

    fun fetchInstructionImageIds(instructionId: UUID): Set<UUID> =
        dsl.select(INSTRUCTION_IMAGES.IMAGE_ID)
            .from(INSTRUCTION_IMAGES)
            .where(INSTRUCTION_IMAGES.INSTRUCTION_ID.eq(instructionId))
            .fetch(INSTRUCTION_IMAGES.IMAGE_ID)
            .filterNotNull()
            .toSet()

    private fun nameSimilarity(term: String) =
        DSL.function("similarity", SQLDataType.DOUBLE, RECIPES.NAME, DSL.value(term))

    fun selectRecipesWithFilters(q: String? = null, userId: UUID? = null): List<Pair<UUID, String>> {
        val conditions = mutableListOf<Condition>()

        q?.let { conditions += DSL.condition("{0} % {1}", RECIPES.NAME, it) }
        userId?.let { conditions += RECIPES.USER_ID.eq(it) }

        return dsl.select(RECIPES.ID, RECIPES.NAME)
            .from(RECIPES)
            .where(conditions)
            .orderBy(if (q != null) nameSimilarity(q).desc() else RECIPES.NAME.asc())
            .limit(100)
            .fetch { Pair(it[RECIPES.ID]!!, it[RECIPES.NAME]!!) }
    }

}