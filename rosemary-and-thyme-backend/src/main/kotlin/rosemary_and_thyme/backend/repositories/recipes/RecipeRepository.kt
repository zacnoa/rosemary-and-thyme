package rosemary_and_thyme.backend.repositories.recipes

import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.Field
import org.jooq.TableLike
import org.jooq.impl.DSL
import org.jooq.impl.SQLDataType
import org.springframework.stereotype.Repository
import rosemary_and_thyme.backend.jooqgenerated.app_auth.tables.records.UsersRecord
import rosemary_and_thyme.backend.jooqgenerated.public.tables.HeroImages.Companion.HERO_IMAGES
import rosemary_and_thyme.backend.jooqgenerated.public.tables.Ingredients.Companion.INGREDIENTS
import rosemary_and_thyme.backend.jooqgenerated.public.tables.InstructionImages.Companion.INSTRUCTION_IMAGES
import rosemary_and_thyme.backend.jooqgenerated.public.tables.Instructions.Companion.INSTRUCTIONS
import rosemary_and_thyme.backend.jooqgenerated.public.tables.Images.Companion.IMAGES
import rosemary_and_thyme.backend.jooqgenerated.public.tables.Recipes.Companion.RECIPES
import rosemary_and_thyme.backend.jooqgenerated.public.tables.RecipeLikes.Companion.RECIPE_LIKES
import rosemary_and_thyme.backend.jooqgenerated.public.tables.records.*
import rosemary_and_thyme.backend.models.recipes.IngredientDTO
import rosemary_and_thyme.backend.models.recipes.InstructionDTO
import rosemary_and_thyme.backend.models.recipes.RecipeWriteDTO
import rosemary_and_thyme.backend.models.recipes.RecipeFeedDTO
import rosemary_and_thyme.backend.models.recipes.RecipeFeedFilter
import rosemary_and_thyme.backend.models.recipes.RecipeFeedPage
import rosemary_and_thyme.backend.models.recipes.RecipeFeedSort
import rosemary_and_thyme.backend.models.recipes.RecipeImageDTO
import rosemary_and_thyme.backend.repositories.dbNotNull
import java.time.OffsetDateTime
import java.util.*
import kotlin.collections.partition

/** Provides database operations for recipes and their related records. */

@Repository
class RecipeRepository(val dsl: DSLContext) {

    companion object {
        /** Page size every cursor-paginated feed query below defaults to when a caller doesn't override it. */
        private const val DEFAULT_PAGE_SIZE = 20
    }

    /** Splits submitted items into updates, inserts, and deletions.
     * @param existingIds ids currently stored for the parent record.
     * @param items all submitted items.
     * @param idOf extracts an item's id.
     * @param order the submitted item order.
     * @return items to update and insert with indexes, plus ids to delete.
     */
    private fun <T> diffByOrder(
        existingIds: Set<UUID>,
        items: Collection<T>,
        idOf: (T) -> UUID,
        order: List<UUID>
    ): Triple<List<Pair<T, Int>>, List<Pair<T, Int>>, Set<UUID>> {
        val (toUpdate, toInsert) = items.partition { idOf(it) in existingIds }
        val toDelete = existingIds - items.map(idOf).toSet()

        return Triple(
            toUpdate.map { it to order.indexOf(idOf(it)) },
            toInsert.map { it to order.indexOf(idOf(it)) },
            toDelete
        )
    }

    /** Inserts or updates a recipe's base row.
     * @param recipeId the existing recipe id, or `null` for a new recipe.
     * @param dto the submitted recipe state.
     * @param user the owner used for a new recipe.
     * @return the persisted recipe id.
     */
    fun upsertRecipeBase(recipeId: UUID?, dto: RecipeWriteDTO, user: UsersRecord): UUID {
        /** check if recipe with given id exists. */
        val existingId = dsl.select(RECIPES.ID)
            .from(RECIPES)
            .where(RECIPES.ID.eq(recipeId))
            .fetchOne(RECIPES.ID)

        /** if there is a recipe update existing row and return id. */
        return if (existingId != null) {
            dsl.update(RECIPES)
                .set(RECIPES.NAME, dto.name)
                .set(RECIPES.DESCRIPTION, dto.description)
                .set(RECIPES.COOK_TIME, dto.cookTime)
                .set(RECIPES.DIFFICULTY, dto.difficulty)
                .set(RECIPES.PORTIONS, dto.portions)
                .set(RECIPES.SIDE_NOTES, dto.sideNotes)
                .where(RECIPES.ID.eq(recipeId))
                .execute()
            existingId
        }
        /** if there is no recipe by that id insert new recipe and return the new id. */
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
                .set(RECIPES.TIMES_MADE, 0)
                .returningResult(RECIPES.ID)
                .fetchOne()
                ?.get(RECIPES.ID) ?: throw IllegalStateException("Failed to insert recipe")
        }
    }

    /** Updates a recipe's visibility flag.
     * @param recipeId the recipe to update.
     * @param isPrivate the requested visibility.
     */
    fun updatePrivate(recipeId: UUID, isPrivate: Boolean) {
        dsl.update(RECIPES)
            .set(RECIPES.IS_PRIVATE, isPrivate)
            .where(RECIPES.ID.eq(recipeId))
            .execute()
    }

    /** Lists recipe ids and names owned by a user.
     * @param userId the recipe owner.
     * @return the user's recipe ids paired with names.
     */
    fun selectAllRecipes(userId: UUID): List<Pair<UUID, String>> =

        dsl.select(RECIPES.ID,RECIPES.NAME).from(RECIPES)
            .where(RECIPES.USER_ID.eq(userId))
            .limit(100)
            .fetch{record -> Pair(record[RECIPES.ID].dbNotNull("id"), record[RECIPES.NAME].dbNotNull("name"))}

    /** Lists every recipe id owned by a user.
     * @param userId the recipe owner.
     * @return the user's recipe ids.
     */
    fun selectAllRecipeIds(userId: UUID): List<UUID> =
        dsl.select(RECIPES.ID)
            .from(RECIPES)
            .where(RECIPES.USER_ID.eq(userId))
            .fetch(RECIPES.ID)
            .filterNotNull()

    /** Synchronizes a recipe's ingredients and their order.
     * @param recipeId the owning recipe.
     * @param ingredients submitted ingredients keyed by id.
     * @param order the submitted ingredient order.
     */
    fun syncIngredients(
        recipeId: UUID,
        ingredients: Map<UUID, IngredientDTO>,
        order: List<UUID>
    ) {
        /** Fetches the stored ingredient ids for comparison with the client state. */
        val existingIds = dsl.select(INGREDIENTS.ID)
            .from(INGREDIENTS)
            .where(INGREDIENTS.RECIPE_ID.eq(recipeId))
            .fetchSet(INGREDIENTS.ID)
            .filterNotNull()
            .toSet()

        val (toUpdate, toInsert, toDelete) = diffByOrder(existingIds, ingredients.values, { it.id }, order)

        /** update all ingredients. */
        if (toUpdate.isNotEmpty()) {
            dsl.batch(
                toUpdate.map { (ing, index) ->
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

        /** insert new recipes. */
        if (toInsert.isNotEmpty()) {
            val result = dsl.batch(
                toInsert.map { (ing, index) ->
                    dsl.insertInto(INGREDIENTS)
                        .set(INGREDIENTS.RECIPE_ID, recipeId)
                        .set(INGREDIENTS.NAME, ing.name)
                        .set(INGREDIENTS.AMOUNT, ing.amount)
                        .set(INGREDIENTS.MEASURING_UNIT, ing.measuringUnit)
                        .set(INGREDIENTS.INDEX, index)
                }
            ).execute()
            check(result.all { it == 1 }) { "Error while inserting ingredients" }
        }

        /** delete ingredients. */
        if (toDelete.isNotEmpty()) {
            dsl.deleteFrom(INGREDIENTS)
                .where(INGREDIENTS.RECIPE_ID.eq(recipeId))
                .and(INGREDIENTS.ID.`in`(toDelete))
                .execute()
        }
    }

    /** Synchronizes a recipe's instructions, images, and order.
     * @param recipeId the owning recipe.
     * @param instructions submitted instructions keyed by id.
     * @param order the submitted instruction order.
     * @param images submitted images keyed by id.
     */
    fun syncInstructions(
        recipeId: UUID,
        instructions: Map<UUID, InstructionDTO>,
        order: List<UUID>,
        images: Map<UUID, RecipeImageDTO>
    ) {

        /** fetch existing id for checking. */
        val existingIds = dsl.select(INSTRUCTIONS.ID)
            .from(INSTRUCTIONS)
            .where(INSTRUCTIONS.RECIPE_ID.eq(recipeId))
            .fetchSet(INSTRUCTIONS.ID)
            .filterNotNull()
            .toSet()

        val (toUpdate, toInsert, toDelete) = diffByOrder(existingIds, instructions.values, { it.id }, order)

        /** update existing instructions. */
        if (toUpdate.isNotEmpty()) {
            dsl.batch(
                toUpdate.map { (inst, index) ->
                    dsl.update(INSTRUCTIONS)
                        .set(INSTRUCTIONS.INSTRUCTION, inst.text)
                        .set(INSTRUCTIONS.INDEX, index)
                        .where(INSTRUCTIONS.ID.eq(inst.id))
                        .and(INSTRUCTIONS.RECIPE_ID.eq(recipeId))
                }
            ).execute()
        }

        /** insert new instructions. */
        if (toInsert.isNotEmpty()) {
            toInsert.forEach { (inst, index) ->
                val newId = dsl.insertInto(INSTRUCTIONS)
                    .set(INSTRUCTIONS.RECIPE_ID, recipeId)
                    .set(INSTRUCTIONS.INSTRUCTION, inst.text)
                    .set(INSTRUCTIONS.INDEX, index)
                    .returningResult(INSTRUCTIONS.ID)
                    .fetchOne()
                    ?.get(INSTRUCTIONS.ID) ?: throw IllegalStateException("Failed to insert instruction")

                syncInstructionImages(newId, inst.images, images)
            }
        }

        /** update image for existing images. */
        toUpdate.forEach { (inst, _) ->
            syncInstructionImages(inst.id, inst.images, images)
        }

        /** cascade setting on instruction Images ensures they are deleted after deleting corresponding instruction. */
        if (toDelete.isNotEmpty()) {
            val imageIds = selectInstructionImageIds(toDelete)
            dsl.deleteFrom(INSTRUCTIONS)
                .where(INSTRUCTIONS.RECIPE_ID.eq(recipeId))
                .and(INSTRUCTIONS.ID.`in`(toDelete))
                .execute()
            deleteImages(imageIds)
        }
    }

    /** Synchronizes a recipe's hero images and order.
     * @param recipeId the owning recipe.
     * @param images submitted images keyed by id.
     * @param order the submitted hero-image order.
     */
    fun syncHeroImages(
        recipeId: UUID,
        images: Map<UUID, RecipeImageDTO>,
        order: List<UUID>
    ) {
        val existingIds = selectHeroImageIds(recipeId)
        val heroImages = order.mapNotNull { images[it] }
        val (toUpdate, toInsert, toDelete) = diffByOrder(existingIds, heroImages, { it.id }, order)

        // UPDATE - spans two tables
        if (toUpdate.isNotEmpty()) {
            // update index in hero_images
            dsl.batch(
                toUpdate.map { (img, index) ->
                    dsl.update(HERO_IMAGES)
                        .set(HERO_IMAGES.INDEX, index)
                        .where(HERO_IMAGES.IMAGE_ID.eq(img.id))
                        .and(HERO_IMAGES.RECIPE_ID.eq(recipeId))
                }
            ).execute()
            // update url in images
            dsl.batch(
                toUpdate.map { (img, _) ->
                    dsl.update(IMAGES)
                        .set(IMAGES.URL, img.url)
                        .where(IMAGES.ID.eq(img.id))
                }
            ).execute()
        }

        // INSERT - spans two tables
        if (toInsert.isNotEmpty()) {
            toInsert.forEach { (img, index) ->
                // 1. insert into images, get the new image_id back
                val imageId = dsl.insertInto(IMAGES)
                    .set(IMAGES.URL, img.url)
                    .returningResult(IMAGES.ID)
                    .fetchOne()?.value1() ?: throw IllegalStateException("Failed to insert image")
                // 2. insert into hero_images with that image_id
                dsl.insertInto(HERO_IMAGES)
                    .set(HERO_IMAGES.IMAGE_ID, imageId)
                    .set(HERO_IMAGES.RECIPE_ID, recipeId)
                    .set(HERO_IMAGES.INDEX, index)
                    .execute()
            }
        }

        // DELETE - CASCADE deletes the matching hero_images row automatically
        if (toDelete.isNotEmpty()) {
            dsl.deleteFrom(IMAGES)
                .where(IMAGES.ID.`in`(toDelete))
                .execute()
        }
    }

    /** Synchronizes one instruction's image order.
     * @param instructionId the owning instruction.
     * @param imageIds the submitted image order.
     * @param images submitted images keyed by id.
     */
    fun syncInstructionImages(
        instructionId: UUID,
        imageIds: List<UUID>,
        images: Map<UUID, RecipeImageDTO>
    ) {
        /** Here we cannot reuse selectInstructionImages because the type Map<UUID, List<String>> loses the id information of individual images. */
        val existingIds = selectInstructionImageIds(instructionId)
        val instructionImages = imageIds.mapNotNull { images[it] }
        val (toUpdate, toInsert, toDelete) = diffByOrder(existingIds, instructionImages, { it.id }, imageIds)

        // UPDATE
        if (toUpdate.isNotEmpty()) {
            // update index in instruction_images
            dsl.batch(
                toUpdate.map { (img, index) ->
                    dsl.update(INSTRUCTION_IMAGES)
                        .set(INSTRUCTION_IMAGES.INDEX, index)
                        .where(INSTRUCTION_IMAGES.IMAGE_ID.eq(img.id))
                        .and(INSTRUCTION_IMAGES.INSTRUCTION_ID.eq(instructionId))
                }
            ).execute()
            // update url in images
            dsl.batch(
                toUpdate.map { (img, _) ->
                    dsl.update(IMAGES)
                        .set(IMAGES.URL, img.url)
                        .where(IMAGES.ID.eq(img.id))
                }
            ).execute()
        }

        // INSERT
        if (toInsert.isNotEmpty()) {
            toInsert.forEach { (img, index) ->
                // 1. insert into images
                val imageId = dsl.insertInto(IMAGES)
                    .set(IMAGES.URL, img.url)
                    .returningResult(IMAGES.ID)
                    .fetchOne()?.value1() ?: throw IllegalStateException("Failed to insert image")
                // 2. insert into instruction_images with that image_id
                dsl.insertInto(INSTRUCTION_IMAGES)
                    .set(INSTRUCTION_IMAGES.IMAGE_ID, imageId)
                    .set(INSTRUCTION_IMAGES.INSTRUCTION_ID, instructionId)
                    .set(INSTRUCTION_IMAGES.INDEX, index)
                    .execute()
            }
        }

        // DELETE - CASCADE deletes the matching instruction_images row automatically
        if (toDelete.isNotEmpty()) {
            dsl.deleteFrom(IMAGES)
                .where(IMAGES.ID.`in`(toDelete))
                .execute()
        }
    }

    /** Finds a recipe base row.
     * @param recipeId the recipe id.
     * @return the recipe row, or `null` when it does not exist.
     */
    fun selectRecipeBase(recipeId: UUID): RecipesRecord? =
        dsl.selectFrom(RECIPES)
            .where(RECIPES.ID.eq(recipeId))
            .fetchOneInto(RecipesRecord::class.java)

    /** Deletes a recipe and unreferenced image rows.
     * @param recipeId the recipe to delete.
     */
    fun deleteRecipeAndImages(recipeId: UUID) {
        val imageIds = selectAllImagesForRecipe(recipeId).keys
        dsl.deleteFrom(RECIPES)
            .where(RECIPES.ID.eq(recipeId))
            .execute()
        deleteImages(imageIds)
    }

    /** Lists a recipe's ingredients.
     * @param recipeId the recipe id.
     * @return ingredient rows in display order.
     */
    fun selectIngredients(recipeId: UUID): List<IngredientsRecord> =
        dsl.selectFrom(INGREDIENTS)
            .where(INGREDIENTS.RECIPE_ID.eq(recipeId))
            .orderBy(INGREDIENTS.INDEX)
            .fetchInto(IngredientsRecord::class.java)

    /** Lists a recipe's instructions.
     * @param recipeId the recipe id.
     * @return instruction rows in display order.
     */
    fun selectInstructions(recipeId: UUID): List<InstructionsRecord> =
        dsl.selectFrom(INSTRUCTIONS)
            .where(INSTRUCTIONS.RECIPE_ID.eq(recipeId))
            .orderBy(INSTRUCTIONS.INDEX)
            .fetchInto(InstructionsRecord::class.java)

    /** Lists a recipe's hero images.
     * @param recipeId the recipe id.
     * @return image ids mapped to delivery URLs.
     */
    fun selectHeroImages(recipeId: UUID): Map<UUID, String> =
        dsl.select(HERO_IMAGES.IMAGE_ID, IMAGES.URL)
            .from(HERO_IMAGES)
            .join(IMAGES)
            .on(HERO_IMAGES.IMAGE_ID.eq(IMAGES.ID))
            .where(HERO_IMAGES.RECIPE_ID.eq(recipeId))
            .orderBy(HERO_IMAGES.INDEX)
            .fetch()
            .associate { it[HERO_IMAGES.IMAGE_ID].dbNotNull("image_id") to it[IMAGES.URL].dbNotNull("url") }

    /** Lists images grouped by instruction id.
     * @param instructionIds instruction ids to query.
     * @return image ids and URLs grouped by instruction.
     */
    fun selectInstructionImages(instructionIds: List<UUID>): Map<UUID, List<Pair<UUID, String>>> =
        dsl.select(INSTRUCTION_IMAGES.INSTRUCTION_ID, INSTRUCTION_IMAGES.IMAGE_ID, IMAGES.URL)
            .from(INSTRUCTION_IMAGES)
            .join(IMAGES).on(INSTRUCTION_IMAGES.IMAGE_ID.eq(IMAGES.ID))
            .where(INSTRUCTION_IMAGES.INSTRUCTION_ID.`in`(instructionIds))
            .orderBy(INSTRUCTION_IMAGES.INDEX)
            .fetch()
            .groupBy(
                { it[INSTRUCTION_IMAGES.INSTRUCTION_ID].dbNotNull("instruction_id") },
                { it[INSTRUCTION_IMAGES.IMAGE_ID].dbNotNull("image_id") to it[IMAGES.URL].dbNotNull("url") }
            )

    /** Lists every image attached to a recipe.
     * @param recipeId the recipe id.
     * @return image ids mapped to delivery URLs.
     */
    fun selectAllImagesForRecipe(recipeId: UUID): Map<UUID, String> {
        val heroImage = dsl.select(HERO_IMAGES.IMAGE_ID, IMAGES.URL)
            .from(HERO_IMAGES)
            .join(IMAGES)
            .on(HERO_IMAGES.IMAGE_ID.eq(IMAGES.ID))
            .where((HERO_IMAGES.RECIPE_ID).eq(recipeId))

        val instructionImages = dsl.select(INSTRUCTION_IMAGES.IMAGE_ID, IMAGES.URL)
            .from(INSTRUCTION_IMAGES)
            .join(INSTRUCTIONS).on(INSTRUCTION_IMAGES.INSTRUCTION_ID.eq(INSTRUCTIONS.ID))
            .join(IMAGES).on(INSTRUCTION_IMAGES.IMAGE_ID.eq(IMAGES.ID))
            .where(INSTRUCTIONS.RECIPE_ID.eq(recipeId))

        return heroImage.union(instructionImages)
            .fetch()
            .associate { it[HERO_IMAGES.IMAGE_ID].dbNotNull("image_id") to it[IMAGES.URL].dbNotNull("url") }
    }

    /** Lists hero-image ids for a recipe.
     * @param recipeId the recipe id.
     * @return the hero-image ids.
     */
    fun selectHeroImageIds(recipeId: UUID): Set<UUID> =
        dsl.select(HERO_IMAGES.IMAGE_ID)
            .from(HERO_IMAGES)
            .where(HERO_IMAGES.RECIPE_ID.eq(recipeId))
            .fetch(HERO_IMAGES.IMAGE_ID)
            .filterNotNull()
            .toSet()

    /** Lists image ids for one instruction.
     * @param instructionId the instruction id.
     * @return the instruction-image ids.
     */
    fun selectInstructionImageIds(instructionId: UUID): Set<UUID> =
        dsl.select(INSTRUCTION_IMAGES.IMAGE_ID)
            .from(INSTRUCTION_IMAGES)
            .where(INSTRUCTION_IMAGES.INSTRUCTION_ID.eq(instructionId))
            .fetch(INSTRUCTION_IMAGES.IMAGE_ID)
            .filterNotNull()
            .toSet()

    private fun selectInstructionImageIds(instructionIds: Collection<UUID>): Set<UUID> {
        if (instructionIds.isEmpty()) return emptySet()
        return dsl.select(INSTRUCTION_IMAGES.IMAGE_ID)
            .from(INSTRUCTION_IMAGES)
            .where(INSTRUCTION_IMAGES.INSTRUCTION_ID.`in`(instructionIds))
            .fetch(INSTRUCTION_IMAGES.IMAGE_ID)
            .filterNotNull()
            .toSet()
    }

    private fun deleteImages(imageIds: Collection<UUID>) {
        if (imageIds.isEmpty()) return
        dsl.deleteFrom(IMAGES)
            .where(IMAGES.ID.`in`(imageIds))
            .execute()
    }

/** Builds PostgreSQL's trigram-similarity expression for a recipe name.
 * @param term the search term.
 * @return the SQL similarity field.
 */
private fun nameSimilarity(term: String) =
        DSL.function("similarity", SQLDataType.DOUBLE, RECIPES.NAME, DSL.value(term))

    /** Builds SQL conditions for a recipe-feed filter. */
    private fun searchConditions(filter: RecipeFeedFilter): List<Condition> {
        val conditions = mutableListOf<Condition>()
        filter.q?.let { conditions += DSL.condition("{0} % {1}", RECIPES.NAME, it) }
        filter.ownerId?.let { conditions += RECIPES.USER_ID.eq(it) }
        filter.likedByUserId?.let { conditions += RECIPE_LIKES.USER_ID.eq(it) }
        if (filter.excludePrivate) conditions += RECIPES.IS_PRIVATE.eq(false)
        return conditions
    }


    /** Selects one cursor-paginated recipe-feed page from a unified filter. */
    fun selectRecipeFeed(
        filter: RecipeFeedFilter,
        cursor: String? = null,
        pageSize: Int = DEFAULT_PAGE_SIZE
    ): RecipeFeedPage {
        require(filter.sort != RecipeFeedSort.LIKED_AT_DESC || filter.likedByUserId != null) {
            "LIKED_AT_DESC requires likedByUserId"
        }
        val conditions = searchConditions(filter).toMutableList()
        val ascending = filter.sort == RecipeFeedSort.NAME_ASC
        val sortKey: Field<*> = when (filter.sort) {
            RecipeFeedSort.NAME_ASC -> RECIPES.NAME
            RecipeFeedSort.RELEVANCE_DESC -> nameSimilarity(requireNotNull(filter.q) { "RELEVANCE_DESC requires q" })
            RecipeFeedSort.LIKES_DESC -> RECIPES.LIKES
            RecipeFeedSort.LIKED_AT_DESC -> RECIPE_LIKES.CREATED_AT
        }
        val sortKeyColumn = sortKey.`as`("sort_key")

        if (cursor != null) {
            val (sortValue, cursorId) = RecipeCursor.decode(cursor)
            conditions += when (filter.sort) {
                RecipeFeedSort.NAME_ASC -> DSL.row(RECIPES.NAME, RECIPES.ID).gt(DSL.row(sortValue, cursorId))
                RecipeFeedSort.RELEVANCE_DESC -> DSL.row(nameSimilarity(requireNotNull(filter.q)), RECIPES.ID)
                    .lt(DSL.row(sortValue.toDouble(), cursorId))
                RecipeFeedSort.LIKES_DESC -> DSL.row(RECIPES.LIKES, RECIPES.ID).lt(DSL.row(sortValue.toLong(), cursorId))
                RecipeFeedSort.LIKED_AT_DESC -> DSL.row(RECIPE_LIKES.CREATED_AT, RECIPES.ID)
                    .lt(DSL.row(OffsetDateTime.parse(sortValue), cursorId))
            }
        }

        val fromTable: TableLike<*> = filter.likedByUserId?.let {
            RECIPE_LIKES.join(RECIPES).on(RECIPE_LIKES.RECIPE_ID.eq(RECIPES.ID))
        } ?: RECIPES

        val rows = dsl.select(
            RECIPES.ID, RECIPES.NAME, RECIPES.DESCRIPTION, RECIPES.LIKES,
            RECIPES.USER_NAME, RECIPES.CREATED_DATE, RECIPES.IS_PRIVATE, sortKeyColumn
        )
            .from(fromTable)
            .where(conditions)
            .orderBy(if (ascending) sortKey.asc() else sortKey.desc(), if (ascending) RECIPES.ID.asc() else RECIPES.ID.desc())
            .limit(pageSize + 1)
            .fetch()

        val page = rows.take(pageSize)
        val nextCursor = if (rows.size > pageSize) {
            val last = page.last()
            RecipeCursor.encode(last[sortKeyColumn].dbNotNull("sort_key").toString(), last[RECIPES.ID].dbNotNull("id"))
        } else null

        return RecipeFeedPage(
            items = page.map {
                RecipeFeedDTO(
                    id = it[RECIPES.ID].dbNotNull("id"),
                    name = it[RECIPES.NAME].dbNotNull("name"),
                    description = it[RECIPES.DESCRIPTION].dbNotNull("description"),
                    likes = it[RECIPES.LIKES].dbNotNull("likes"),
                    userName = it[RECIPES.USER_NAME].dbNotNull("user_name"),
                    createDate = it[RECIPES.CREATED_DATE].dbNotNull("created_date"),
                    heroImageUrl = null,
                    isPrivate = it[RECIPES.IS_PRIVATE].dbNotNull("is_private")
                )
            },
            nextCursor = nextCursor
        )
    }


    /** Finds the first hero image for each recipe.
     * @param recipeIds the recipes to query.
     * @return recipe ids mapped to first hero-image URLs.
     */
    fun selectFirstHeroImages(recipeIds: List<UUID>): Map<UUID, String> {
        if (recipeIds.isEmpty()) return emptyMap()

        return dsl.select(HERO_IMAGES.RECIPE_ID, IMAGES.URL)
            .distinctOn(HERO_IMAGES.RECIPE_ID)
            .from(HERO_IMAGES)
            .join(IMAGES).on(HERO_IMAGES.IMAGE_ID.eq(IMAGES.ID))
            .where(HERO_IMAGES.RECIPE_ID.`in`(recipeIds))
            .orderBy(HERO_IMAGES.RECIPE_ID, HERO_IMAGES.INDEX)
            .fetch()
            .associate { it[HERO_IMAGES.RECIPE_ID].dbNotNull("recipe_id") to it[IMAGES.URL].dbNotNull("url") }
    }

    /** Checks whether a user liked a recipe.
     * @param recipeId the recipe id.
     * @param userId the user id.
     * @return whether the like exists.
     */
    fun hasUserLikedRecipe(recipeId: UUID, userId: UUID): Boolean =
        dsl.fetchExists(
            dsl.selectOne()
                .from(RECIPE_LIKES)
                .where(RECIPE_LIKES.RECIPE_ID.eq(recipeId))
                .and(RECIPE_LIKES.USER_ID.eq(userId))
        )

    /** Inserts a recipe like when it does not already exist.
     * @param recipeId the liked recipe.
     * @param userId the user adding the like.
     * @return whether a row was inserted.
     */
    fun insertLike(recipeId: UUID, userId: UUID): Boolean =
        dsl.insertInto(RECIPE_LIKES)
            .set(RECIPE_LIKES.RECIPE_ID, recipeId)
            .set(RECIPE_LIKES.USER_ID, userId)
            .onConflictDoNothing()
            .execute() == 1

    /** Deletes a recipe like when it exists.
     * @param recipeId the liked recipe.
     * @param userId the user removing the like.
     * @return whether a row was deleted.
     */
    fun deleteLike(recipeId: UUID, userId: UUID): Boolean =
        dsl.deleteFrom(RECIPE_LIKES)
            .where(RECIPE_LIKES.RECIPE_ID.eq(recipeId))
            .and(RECIPE_LIKES.USER_ID.eq(userId))
            .execute() == 1

    /** Adjusts a recipe's cached like count.
     * @param recipeId the recipe to update.
     * @param delta the count increment or decrement.
     */
    fun incrementLikes(recipeId: UUID, delta: Long) {
        dsl.update(RECIPES)
            .set(RECIPES.LIKES, RECIPES.LIKES.plus(delta))
            .where(RECIPES.ID.eq(recipeId))
            .execute()
    }

    /** Reads a recipe's cached like count.
     * @param recipeId the recipe id.
     * @return the current like count.
     */
    fun selectLikesCount(recipeId: UUID): Long =
        dsl.select(RECIPES.LIKES)
            .from(RECIPES)
            .where(RECIPES.ID.eq(recipeId))
            .fetchOne(RECIPES.LIKES)
            .dbNotNull("likes")

    /** Atomically records one completed preparation of a recipe.
     * @param recipeId the recipe that was made.
     * @return the updated lifetime count.
     */
    fun incrementTimesMade(recipeId: UUID): Long =
        dsl.update(RECIPES)
            .set(RECIPES.TIMES_MADE, RECIPES.TIMES_MADE.plus(1))
            .where(RECIPES.ID.eq(recipeId))
            .returningResult(RECIPES.TIMES_MADE)
            .fetchOne()
            ?.get(RECIPES.TIMES_MADE)
            .dbNotNull("timesMade")

}
