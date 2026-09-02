package rosemary_and_thyme.backend.repositories.recipes

import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.Field
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
import rosemary_and_thyme.backend.models.recipes.RecipeFeedPage
import rosemary_and_thyme.backend.models.recipes.RecipeImageDTO
import rosemary_and_thyme.backend.repositories.dbNotNull
import java.time.OffsetDateTime
import java.util.*
import kotlin.collections.partition

/**
 * Repository layer.
 */

@Repository
class RecipeRepository(val dsl: DSLContext) {

    companion object {
        /**
 * Page size every cursor-paginated feed query below defaults to when a caller doesn't override it.
 */
        private const val DEFAULT_PAGE_SIZE = 20
    }

    /**
 * Reconciles a client-submitted "desired state" (items + the order they should.
 * @param existingIds ids currently in the DB for this parent (e.g. this recipe's ingredient ids)
 * @param items the client's full submitted set of items.
 * @param idOf extracts an item's id, since [T] isn't assumed to expose one directly.
 * @param order the client's submitted display order (a list of ids); an item's.
 * @return a [Triple] of (items to update with their target index, items to insert.
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

    /**
 * Inserts or updates a recipe's base row.
 * @param dto the recipe's desired state.
 * @param user the authenticated owner used when inserting a recipe.
 * @return the existing ID or the newly generated ID.
 */
    fun upsertRecipeBase(recipeId: UUID?, dto: RecipeWriteDTO, user: UsersRecord): UUID {
        /**
 * check if recipe with given id exists.
 */
        val existingId = dsl.select(RECIPES.ID)
            .from(RECIPES)
            .where(RECIPES.ID.eq(recipeId))
            .fetchOne(RECIPES.ID)

        /**
 * if there is a recipe update existing row and return id.
 */
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
        /**
 * if there is no recipe by that id insert new recipe and return the new id.
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

    /**
 * Flips a recipe's [rosemary_and_thyme.backend.models.recipes.RecipeDTO.isPrivate].
 * @param recipeId the recipe to update.
 * @param isPrivate the new value.
 */
    fun updatePrivate(recipeId: UUID, isPrivate: Boolean) {
        dsl.update(RECIPES)
            .set(RECIPES.IS_PRIVATE, isPrivate)
            .where(RECIPES.ID.eq(recipeId))
            .execute()
    }

    /**
 * Returns up to 100 recipes owned by a user without filtering.
 * @param userId the owner to list recipes for.
 * @return up to 100 recipe ID/name pairs.
 */
    fun selectAllRecipes(userId: UUID): List<Pair<UUID, String>> =

        dsl.select(RECIPES.ID,RECIPES.NAME).from(RECIPES)
            .where(RECIPES.USER_ID.eq(userId))
            .limit(100)
            .fetch{record -> Pair(record[RECIPES.ID].dbNotNull("id"), record[RECIPES.NAME].dbNotNull("name"))}

    /**
 * Returns every recipe ID owned by a user.
 * @param userId the owner whose recipe ids to fetch.
 * @return every recipe id owned by [userId].
 */
    fun selectAllRecipeIds(userId: UUID): List<UUID> =
        dsl.select(RECIPES.ID)
            .from(RECIPES)
            .where(RECIPES.USER_ID.eq(userId))
            .fetch(RECIPES.ID)
            .filterNotNull()

    /**
 * Reconciles a recipe's ingredients against the client's submitted state (see.
 * @param recipeId the recipe the ingredients belong to.
 * @param ingredients the client's full submitted set of ingredients, keyed by id.
 * @param order the client's submitted display order for [ingredients].
 */
    fun syncIngredients(
        recipeId: UUID,
        ingredients: Map<UUID, IngredientDTO>,
        order: List<UUID>
    ) {
        /**
 * fetch existing ids for checking with client state.
 */
        val existingIds = dsl.select(INGREDIENTS.ID)
            .from(INGREDIENTS)
            .where(INGREDIENTS.RECIPE_ID.eq(recipeId))
            .fetchSet(INGREDIENTS.ID)
            .filterNotNull()
            .toSet()

        val (toUpdate, toInsert, toDelete) = diffByOrder(existingIds, ingredients.values, { it.id }, order)

        /**
 * update all ingredients.
 */
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

        /**
 * insert new recipes.
 */
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

        /**
 * delete ingredients.
 */
        if (toDelete.isNotEmpty()) {
            dsl.deleteFrom(INGREDIENTS)
                .where(INGREDIENTS.RECIPE_ID.eq(recipeId))
                .and(INGREDIENTS.ID.`in`(toDelete))
                .execute()
        }
    }

    /**
 * Reconciles a recipe's instructions against the client's submitted state (see.
 * @param recipeId the recipe the instructions belong to.
 * @param instructions the client's full submitted set of instruction steps, keyed by id.
 * @param order the client's submitted display order for [instructions].
 * @param images every image in the recipe (hero + instruction), keyed by id - passed.
 */
    fun syncInstructions(
        recipeId: UUID,
        instructions: Map<UUID, InstructionDTO>,
        order: List<UUID>,
        images: Map<UUID, RecipeImageDTO>
    ) {

        /**
 * fetch existing id for checking.
 */
        val existingIds = dsl.select(INSTRUCTIONS.ID)
            .from(INSTRUCTIONS)
            .where(INSTRUCTIONS.RECIPE_ID.eq(recipeId))
            .fetchSet(INSTRUCTIONS.ID)
            .filterNotNull()
            .toSet()

        val (toUpdate, toInsert, toDelete) = diffByOrder(existingIds, instructions.values, { it.id }, order)

        /**
 * update existing instructions.
 */
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

        /**
 * insert new instructions.
 */
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

        /**
 * update image for existing images.
 */
        toUpdate.forEach { (inst, _) ->
            syncInstructionImages(inst.id, inst.images, images)
        }

        /**
 * cascade setting on instruction Images ensures they are deleted after deleting corresponding instruction.
 */
        if (toDelete.isNotEmpty()) {
            val imageIds = selectInstructionImageIds(toDelete)
            dsl.deleteFrom(INSTRUCTIONS)
                .where(INSTRUCTIONS.RECIPE_ID.eq(recipeId))
                .and(INSTRUCTIONS.ID.`in`(toDelete))
                .execute()
            deleteImages(imageIds)
        }
    }

    /**
 * Reconciles a recipe's hero images against the client's submitted state (see.
 * @param recipeId the recipe the hero images belong to.
 * @param images every image in the recipe (hero + instruction) keyed by id; only.
 * @param order the client's submitted display order of hero image ids.
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

    /**
 * Reconciles a single instruction step's images against the client's submitted.
 * @param instructionId the instruction step the images belong to.
 * @param imageIds the client's submitted display order of this step's image ids.
 * @param images every image in the recipe (hero + instruction), keyed by id -.
 */
    fun syncInstructionImages(
        instructionId: UUID,
        imageIds: List<UUID>,
        images: Map<UUID, RecipeImageDTO>
    ) {
        /**
 * Here we cannot reuse selectInstructionImages because the type Map<UUID, List<String>> loses the id information of individual images.
 */
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

    /**
 * Describes this operation.
 * @param recipeId the recipe's id.
 * @return the recipe's base row, or `null` if no recipe exists with this id.
 */
    fun selectRecipeBase(recipeId: UUID): RecipesRecord? =
        dsl.selectFrom(RECIPES)
            .where(RECIPES.ID.eq(recipeId))
            .fetchOneInto(RecipesRecord::class.java)

    /**
 * Deletes a recipe and its unreferenced image rows in the same transaction.
 */
    fun deleteRecipeAndImages(recipeId: UUID) {
        val imageIds = selectAllImagesForRecipe(recipeId).keys
        dsl.deleteFrom(RECIPES)
            .where(RECIPES.ID.eq(recipeId))
            .execute()
        deleteImages(imageIds)
    }

    /**
 * Describes this operation.
 * @return the recipe's ingredients, in display order.
 */
    fun selectIngredients(recipeId: UUID): List<IngredientsRecord> =
        dsl.selectFrom(INGREDIENTS)
            .where(INGREDIENTS.RECIPE_ID.eq(recipeId))
            .orderBy(INGREDIENTS.INDEX)
            .fetchInto(IngredientsRecord::class.java)

    /**
 * Describes this operation.
 * @return the recipe's instruction steps, in display order.
 */
    fun selectInstructions(recipeId: UUID): List<InstructionsRecord> =
        dsl.selectFrom(INSTRUCTIONS)
            .where(INSTRUCTIONS.RECIPE_ID.eq(recipeId))
            .orderBy(INSTRUCTIONS.INDEX)
            .fetchInto(InstructionsRecord::class.java)

    /**
 * Describes this operation.
 * @return the recipe's hero images as imageId -> url, in display order.
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

    /**
 * instruction of one recipe (see [rosemary_and_thyme.backend.services.recipes.RecipeService.findRecipeById])
 * @param instructionIds instruction ids to fetch images for, e.g. every.
 * @return images grouped by their owning instruction id, each as (imageId, url)
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

    /**
 * All images currently attached to a recipe (hero + instruction images), keyed by.
 * @param recipeId the recipe to fetch every image for.
 * @return recipe's current images, keyed by id, as a "before" snapshot for [rosemary_and_thyme.backend.services.recipes.RecipeService.saveRecipe].
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

    /**
 * Describes this operation.
 * @return ids of the recipe's current hero images, unordered.
 */
    fun selectHeroImageIds(recipeId: UUID): Set<UUID> =
        dsl.select(HERO_IMAGES.IMAGE_ID)
            .from(HERO_IMAGES)
            .where(HERO_IMAGES.RECIPE_ID.eq(recipeId))
            .fetch(HERO_IMAGES.IMAGE_ID)
            .filterNotNull()
            .toSet()

    /**
 * Describes this operation.
 * @return ids of the instruction step's current images, unordered.
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

    /**
 * Postgres `pg_trgm` similarity score between a recipe's name and [term], used to the associated behavior.
 */
private fun nameSimilarity(term: String) =
        DSL.function("similarity", SQLDataType.DOUBLE, RECIPES.NAME, DSL.value(term))

    /**
 * Shared by recipe-feed queries - both filter recipes.
 * @param q optional name filter, matched with Postgres's trigram `%` similarity.
 * @param userId optional owner filter.
 * @param excludePrivate when `true`, adds `is_private = false` - used by the.
 */
    private fun searchConditions(q: String?, userId: UUID?, excludePrivate: Boolean = false): List<Condition> {
        val conditions = mutableListOf<Condition>()
        q?.let { conditions += DSL.condition("{0} % {1}", RECIPES.NAME, it) }
        userId?.let { conditions += RECIPES.USER_ID.eq(it) }
        if (excludePrivate) conditions += RECIPES.IS_PRIVATE.eq(false)
        return conditions
    }

    /**
 * Shared by recipe-feed queries. Global feeds order by likes; dashboard feeds.
 */
    private fun searchOrder(q: String?, orderByLikes: Boolean) = when {
        orderByLikes -> RECIPES.LIKES.desc()
        q != null -> nameSimilarity(q).desc()
        else -> RECIPES.NAME.asc()
    }

    /**
 * Fuzzy name/owner filtering and ordering, selecting the full set of columns a feed card.
 * @param q optional name filter.
 * @param userId optional owner filter.
 * @param orderByLikes whether to order by popularity rather than name relevance.
 * @param excludePrivate see [searchConditions].
 * @param cursor an earlier call's [RecipeFeedPage.nextCursor], or `null` for the.
 * @param pageSize how many recipes to return; capped by callers, not validated here.
 * @return up to [pageSize] matching recipes as feed cards (hero image not yet.
 */
    fun selectRecipeFeed(
        q: String? = null,
        userId: UUID? = null,
        orderByLikes: Boolean = false,
        excludePrivate: Boolean = false,
        cursor: String? = null,
        pageSize: Int = DEFAULT_PAGE_SIZE
    ): RecipeFeedPage {
        val conditions = searchConditions(q, userId, excludePrivate).toMutableList()

        // The raw sort-key expression - reused (not the aliased sortKeyColumn below)
        // in both ORDER BY and the cursor condition, since Postgres can't reference a
        // SELECT-list alias from WHERE. RECIPES.ID is always the tiebreak, appended
        // to both, so ties on the primary key (e.g. two recipes with equal likes)
        // still sort/paginate deterministically.
        val ascending = !orderByLikes && q == null
        val sortKey: Field<*> = when {
            orderByLikes -> RECIPES.LIKES
            q != null -> nameSimilarity(q)
            else -> RECIPES.NAME
        }
        val sortKeyColumn = sortKey.`as`("sort_key")

        if (cursor != null) {
            val (sortValue, cursorId) = RecipeCursor.decode(cursor)
            conditions += when {
                orderByLikes -> DSL.row(RECIPES.LIKES, RECIPES.ID).lt(DSL.row(sortValue.toLong(), cursorId))
                q != null -> DSL.row(nameSimilarity(q), RECIPES.ID).lt(DSL.row(sortValue.toDouble(), cursorId))
                else -> DSL.row(RECIPES.NAME, RECIPES.ID).gt(DSL.row(sortValue, cursorId))
            }
        }

        val rows = dsl.select(
            RECIPES.ID, RECIPES.NAME, RECIPES.DESCRIPTION, RECIPES.LIKES,
            RECIPES.USER_NAME, RECIPES.CREATED_DATE, RECIPES.IS_PRIVATE, sortKeyColumn
        )
            .from(RECIPES)
            .where(conditions)
            .orderBy(if (ascending) sortKey.asc() else sortKey.desc(), if (ascending) RECIPES.ID.asc() else RECIPES.ID.desc())
            // pageSize + 1: the extra row (if it comes back) is never returned to the
            // caller, only its presence is - that's how hasMore/nextCursor below is
            // known without a separate COUNT(*) query.
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

    /**
 * Returns a user's liked recipes ordered by like time.
 * @param userId the user whose likes to list.
 * @param cursor an earlier call's [RecipeFeedPage.nextCursor], or `null` for the first page.
 * @param pageSize how many recipes to return; capped by callers, not validated here.
 * @return a page of liked recipes as feed cards.
 */
    fun selectLikedRecipeFeed(
        userId: UUID,
        cursor: String? = null,
        pageSize: Int = DEFAULT_PAGE_SIZE
    ): RecipeFeedPage {
        val conditions = mutableListOf(RECIPE_LIKES.USER_ID.eq(userId))

        if (cursor != null) {
            val (sortValue, cursorId) = RecipeCursor.decode(cursor)
            conditions += DSL.row(RECIPE_LIKES.CREATED_AT, RECIPES.ID)
                .lt(DSL.row(OffsetDateTime.parse(sortValue), cursorId))
        }

        val sortKeyColumn = RECIPE_LIKES.CREATED_AT.`as`("sort_key")

        val rows = dsl.select(
            RECIPES.ID, RECIPES.NAME, RECIPES.DESCRIPTION, RECIPES.LIKES,
            RECIPES.USER_NAME, RECIPES.CREATED_DATE, RECIPES.IS_PRIVATE, sortKeyColumn
        )
            .from(RECIPE_LIKES)
            .join(RECIPES).on(RECIPE_LIKES.RECIPE_ID.eq(RECIPES.ID))
            .where(conditions)
            .orderBy(RECIPE_LIKES.CREATED_AT.desc(), RECIPES.ID.desc())
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

    /**
 * First (lowest-index) hero image url for each of [recipeIds], one row per recipe.
 * @param recipeIds recipes to fetch a preview image for, e.g. one page of.
 * @return recipeId -> that recipe's first hero image url; a recipe with no hero.
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

    /**
 * Describes this operation.
 * @return whether [userId] has already liked [recipeId] - backs the `liked` field on [RecipeDTO].
 */
    fun hasUserLikedRecipe(recipeId: UUID, userId: UUID): Boolean =
        dsl.fetchExists(
            dsl.selectOne()
                .from(RECIPE_LIKES)
                .where(RECIPE_LIKES.RECIPE_ID.eq(recipeId))
                .and(RECIPE_LIKES.USER_ID.eq(userId))
        )

    /**
 * Records that [userId] likes [recipeId], if they don't already. `ON CONFLICT DO.
 * @return `true` if a new like row was actually inserted (so the caller should.
 */
    fun insertLike(recipeId: UUID, userId: UUID): Boolean =
        dsl.insertInto(RECIPE_LIKES)
            .set(RECIPE_LIKES.RECIPE_ID, recipeId)
            .set(RECIPE_LIKES.USER_ID, userId)
            .onConflictDoNothing()
            .execute() == 1

    /**
 * Removes [userId]'s like on [recipeId], if one exists.
 * @return `true` if a like row was actually deleted (so the caller should also.
 */
    fun deleteLike(recipeId: UUID, userId: UUID): Boolean =
        dsl.deleteFrom(RECIPE_LIKES)
            .where(RECIPE_LIKES.RECIPE_ID.eq(recipeId))
            .and(RECIPE_LIKES.USER_ID.eq(userId))
            .execute() == 1

    /**
 * Adjusts a recipe's cached like counter. [delta] is `+1`/`-1` in practice (see.
 */
    fun incrementLikes(recipeId: UUID, delta: Long) {
        dsl.update(RECIPES)
            .set(RECIPES.LIKES, RECIPES.LIKES.plus(delta))
            .where(RECIPES.ID.eq(recipeId))
            .execute()
    }

    /**
 * Describes this operation.
 * @return the recipe's current like count, straight from the DB (not a caller-computed value).
 */
    fun selectLikesCount(recipeId: UUID): Long =
        dsl.select(RECIPES.LIKES)
            .from(RECIPES)
            .where(RECIPES.ID.eq(recipeId))
            .fetchOne(RECIPES.LIKES)
            .dbNotNull("likes")

}
