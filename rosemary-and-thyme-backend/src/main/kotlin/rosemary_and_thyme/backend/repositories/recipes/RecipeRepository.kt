package rosemary_and_thyme.backend.repositories.recipes

import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.jooq.impl.SQLDataType
import org.springframework.stereotype.Repository
import rosemary_and_thyme.backend.jooqgenerated.app_auth.tables.records.UsersRecord
import rosemary_and_thyme.backend.jooqgenerated.public.tables.HeroImages.Companion.HERO_IMAGES
import rosemary_and_thyme.backend.jooqgenerated.public.tables.Ingredients.Companion.INGREDIENTS
import rosemary_and_thyme.backend.jooqgenerated.public.tables.InstructionImages.Companion.INSTRUCTION_IMAGES
import rosemary_and_thyme.backend.jooqgenerated.public.tables.Instructions.Companion.INSTRUCTIONS
import rosemary_and_thyme.backend.jooqgenerated.public.tables.Recipes.Companion.RECIPES
import rosemary_and_thyme.backend.jooqgenerated.public.tables.RecipeLikes.Companion.RECIPE_LIKES
import rosemary_and_thyme.backend.jooqgenerated.public.tables.records.*
import rosemary_and_thyme.backend.jooqgenerated.public.tables.references.IMAGES
import rosemary_and_thyme.backend.models.recipes.IngredientDTO
import rosemary_and_thyme.backend.models.recipes.InstructionDTO
import rosemary_and_thyme.backend.models.recipes.RecipeDTO
import rosemary_and_thyme.backend.models.recipes.RecipeFeedDTO
import rosemary_and_thyme.backend.models.recipes.RecipeImageDTO
import rosemary_and_thyme.backend.repositories.dbNotNull
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

    /**
     * Reconciles a client-submitted "desired state" (items + the order they should
     * appear in) against the set of ids currently in the DB, and pairs each surviving
     * item with the index it should be stored at.
     *
     * The client always sends its full current view of a list (ingredients,
     * instructions, hero images, ...), never an incremental diff, so this is what
     * turns "here is everything that should exist now" into concrete SQL operations:
     * an id in both [items] and [existingIds] is an update, an id in [items] but not
     * [existingIds] is an insert (or, in practice, an item the client generated a fresh
     * client-side id for), and an id in [existingIds] but no longer in [items] is a
     * delete. [order] supplies the position (its index in that list) each surviving
     * item should be persisted at, since display order matters for all of these lists.
     *
     * Shared by every save*() below - they only differ in which jOOQ table they then
     * write the result to.
     *
     * @param existingIds ids currently in the DB for this parent (e.g. this recipe's ingredient ids)
     * @param items the client's full submitted set of items
     * @param idOf extracts an item's id, since [T] isn't assumed to expose one directly
     * @param order the client's submitted display order (a list of ids); an item's
     * index in this list becomes its persisted `index` column
     * @return a [Triple] of (items to update with their target index, items to insert
     * with their target index, ids to delete)
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
     * Upserts a recipe's base row (everything except ingredients/instructions/images,
     * which are saved separately - see [syncIngredients], [syncInstructions], [syncHeroImages]).
     *
     * @param dto the recipe's desired state
     * @param user the authenticated caller; only used as the owner on insert - never
     * trusted from [dto] (see the inline comment below)
     * @return the recipe's id (the existing id if this was an update, a new
     * DB-generated id if this was an insert)
     */
    fun upsertRecipeBase(dto: RecipeDTO, user: UsersRecord): UUID {
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

    /**
     * @param userId the owner to list recipes for
     * @return up to 100 of the user's recipes as (id, name) pairs. Unlike
     * [selectRecipesWithFilters], this has no name filter and sorts by nothing in
     * particular (DB/index order) - it is not currently called from any controller.
     */
    fun selectAllRecipes(userId: UUID): List<Pair<UUID, String>> =

        dsl.select(RECIPES.ID,RECIPES.NAME).from(RECIPES)
            .where(RECIPES.USER_ID.eq(userId))
            .limit(100)
            .fetch{record -> Pair(record[RECIPES.ID].dbNotNull("id"), record[RECIPES.NAME].dbNotNull("name"))}


    /**
     * Reconciles a recipe's ingredients against the client's submitted state (see
     * [diffByOrder]) and applies the update/insert/delete in as few round trips as
     * `jOOQ` allows.
     *
     * Batch execution: [dsl.batch] sends every update (or every insert) as one
     * multi-statement/prepared-batch round trip to Postgres instead of one round trip
     * per row - for a recipe with, say, 15 ingredients being reordered, that is 1
     * network round trip instead of 15. Individual per-row `.execute()` calls each pay
     * the full network latency cost separately, so for anything but a single-row change
     * batching is a large, easy win with no behavior difference.
     *
     * @param recipeId the recipe the ingredients belong to
     * @param ingredients the client's full submitted set of ingredients, keyed by id
     * @param order the client's submitted display order for [ingredients]
     */
    fun syncIngredients(
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
            .filterNotNull()
            .toSet()

        val (toUpdate, toInsert, toDelete) = diffByOrder(existingIds, ingredients.values, { it.id }, order)

        /**
         * update all ingredients
         * we use batch to avoid unnecessary calls to the database
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
         * insert new recipes
         * we don't send ingredient id because we want the database to be the sole UUID generator
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
         * delete ingredients
         */
        if (toDelete.isNotEmpty()) {
            dsl.deleteFrom(INGREDIENTS)
                .where(INGREDIENTS.RECIPE_ID.eq(recipeId))
                .and(INGREDIENTS.ID.`in`(toDelete))
                .execute()
        }
    }

    /**
     * Reconciles a recipe's instructions against the client's submitted state (see
     * [diffByOrder] and the batching note on [syncIngredients], which applies here too
     * for the update/insert steps). Unlike ingredients, instructions also own images
     * (see [syncInstructionImages]), so new instructions are inserted one at a time
     * (not batched) - each insert needs its DB-generated id back immediately to save
     * that instruction's images against it.
     *
     * @param recipeId the recipe the instructions belong to
     * @param instructions the client's full submitted set of instruction steps, keyed by id
     * @param order the client's submitted display order for [instructions]
     * @param images every image in the recipe (hero + instruction), keyed by id - passed
     * through to [syncInstructionImages] to resolve each instruction's image ids to their DTOs
     */
    fun syncInstructions(
        recipeId: UUID,
        instructions: Map<UUID, InstructionDTO>,
        order: List<UUID>,
        images: Map<UUID, RecipeImageDTO>
    ) {

        /**
         * fetch existing id for checking
         */
        val existingIds = dsl.select(INSTRUCTIONS.ID)
            .from(INSTRUCTIONS)
            .where(INSTRUCTIONS.RECIPE_ID.eq(recipeId))
            .fetchSet(INSTRUCTIONS.ID)
            .filterNotNull()
            .toSet()


        val (toUpdate, toInsert, toDelete) = diffByOrder(existingIds, instructions.values, { it.id }, order)

        /**
         * update existing instructions
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
         * insert new instructions
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
         * update image for existing images
         */
        toUpdate.forEach { (inst, _) ->
            syncInstructionImages(inst.id, inst.images, images)
        }

        /**
         * cascade setting on instruction Images ensures they are deleted after deleting corresponding instruction
         */
        if (toDelete.isNotEmpty()) {
            dsl.deleteFrom(INSTRUCTIONS)
                .where(INSTRUCTIONS.RECIPE_ID.eq(recipeId))
                .and(INSTRUCTIONS.ID.`in`(toDelete))
                .execute()
        }
    }

    /**
     * Reconciles a recipe's hero images against the client's submitted state (see
     * [diffByOrder]). Hero images are split across two tables - `images` (the url) and
     * `hero_images` (the recipe/index join row) - so every branch below has to touch
     * both, unlike the single-table ingredients/instructions cases.
     *
     * @param recipeId the recipe the hero images belong to
     * @param images every image in the recipe (hero + instruction) keyed by id; only
     * the ids present in [order] are treated as hero images
     * @param order the client's submitted display order of hero image ids
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
     * Reconciles a single instruction step's images against the client's submitted
     * state (see [diffByOrder]). Same two-table shape as [syncHeroImages]
     * (`images` + `instruction_images`), just scoped to one instruction instead of the
     * whole recipe.
     *
     * @param instructionId the instruction step the images belong to
     * @param imageIds the client's submitted display order of this step's image ids
     * (doubles as both the "desired items" and the "order" argument to [diffByOrder],
     * since an instruction's images have no separate ordering concept)
     * @param images every image in the recipe (hero + instruction), keyed by id -
     * looked up here to resolve [imageIds] to their url/DTO
     */
    fun syncInstructionImages(
        instructionId: UUID,
        imageIds: List<UUID>,
        images: Map<UUID, RecipeImageDTO>
    ) {
        /**
         * Here we cannot reuse selectInstructionImages because the type Map<UUID, List<String>> loses the id information of individual images
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
     * @param recipeId the recipe's id
     * @return the recipe's base row, or `null` if no recipe exists with this id
     */
    fun selectRecipeBase(recipeId: UUID): RecipesRecord? =
        dsl.selectFrom(RECIPES)
            .where(RECIPES.ID.eq(recipeId))
            .fetchOneInto(RecipesRecord::class.java)

    /**
     * Deletes a recipe's base row - `ON DELETE CASCADE` (db/schema.sql) takes every
     * ingredient/instruction/hero_image/instruction_image/recipe_likes/images row that
     * referenced it along with it, so this one statement is the entire DB side of
     * deleting a recipe (see [rosemary_and_thyme.backend.services.recipes.RecipeService.deleteRecipe],
     * the only caller, which handles the non-DB Cloudinary cleanup separately).
     */
    fun deleteRecipeBase(recipeId: UUID) {
        dsl.deleteFrom(RECIPES)
            .where(RECIPES.ID.eq(recipeId))
            .execute()
    }

    /** @return the recipe's ingredients, in display order */
    fun selectIngredients(recipeId: UUID): List<IngredientsRecord> =
        dsl.selectFrom(INGREDIENTS)
            .where(INGREDIENTS.RECIPE_ID.eq(recipeId))
            .orderBy(INGREDIENTS.INDEX)
            .fetchInto(IngredientsRecord::class.java)

    /** @return the recipe's instruction steps, in display order */
    fun selectInstructions(recipeId: UUID): List<InstructionsRecord> =
        dsl.selectFrom(INSTRUCTIONS)
            .where(INSTRUCTIONS.RECIPE_ID.eq(recipeId))
            .orderBy(INSTRUCTIONS.INDEX)
            .fetchInto(InstructionsRecord::class.java)

    /** @return the recipe's hero images as imageId -> url, in display order */
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
     * @param instructionIds instruction ids to fetch images for, e.g. every
     * instruction of one recipe (see [rosemary_and_thyme.backend.services.recipes.RecipeService.findRecipeById])
     * @return images grouped by their owning instruction id, each as (imageId, url)
     * pairs in display order
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
     * All images currently attached to a recipe (hero + instruction images), keyed by
     * id with their Cloudinary url - used to find what a save actually needs to delete,
     * since the client only ever sends the images it wants to *keep*.
     *
     * May cause problems with different combination of columns
     *
     * @param recipeId the recipe to fetch every image for
     * @return recipe's current images, keyed by id, as a "before" snapshot for [rosemary_and_thyme.backend.services.recipes.RecipeService.saveRecipe]
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

    /** @return ids of the recipe's current hero images, unordered */
    fun selectHeroImageIds(recipeId: UUID): Set<UUID> =
        dsl.select(HERO_IMAGES.IMAGE_ID)
            .from(HERO_IMAGES)
            .where(HERO_IMAGES.RECIPE_ID.eq(recipeId))
            .fetch(HERO_IMAGES.IMAGE_ID)
            .filterNotNull()
            .toSet()

    /** @return ids of the instruction step's current images, unordered */
    fun selectInstructionImageIds(instructionId: UUID): Set<UUID> =
        dsl.select(INSTRUCTION_IMAGES.IMAGE_ID)
            .from(INSTRUCTION_IMAGES)
            .where(INSTRUCTION_IMAGES.INSTRUCTION_ID.eq(instructionId))
            .fetch(INSTRUCTION_IMAGES.IMAGE_ID)
            .filterNotNull()
            .toSet()

    /**
     * Postgres `pg_trgm` similarity score between a recipe's name and [term], used to
     * rank fuzzy search results by relevance (see [selectRecipesWithFilters]). Backed
     * by the `recipes_name_trgm_idx` GIN index (db/indexes.sql) so this doesn't require
     * a sequential scan.
     */
    private fun nameSimilarity(term: String) =
        DSL.function("similarity", SQLDataType.DOUBLE, RECIPES.NAME, DSL.value(term))

    /**
     * Shared by [selectRecipesWithFilters] and [selectRecipeFeed] - both filter recipes
     * by the same optional name/owner criteria, they only differ in which columns they
     * select and what they turn each row into.
     *
     * @param q optional name filter, matched with Postgres's trigram `%` similarity
     * operator (via `pg_trgm`) rather than an exact/prefix match, so typos and partial
     * words still find results
     * @param userId optional owner filter
     */
    private fun searchConditions(q: String?, userId: UUID?): List<Condition> {
        val conditions = mutableListOf<Condition>()
        q?.let { conditions += DSL.condition("{0} % {1}", RECIPES.NAME, it) }
        userId?.let { conditions += RECIPES.USER_ID.eq(it) }
        return conditions
    }

    /**
     * Shared by [selectRecipesWithFilters] and [selectRecipeFeed] - see
     * [selectRecipesWithFilters]'s [orderByLikes] doc for the full reasoning behind the
     * three-way choice here.
     */
    private fun searchOrder(q: String?, orderByLikes: Boolean) = when {
        orderByLikes -> RECIPES.LIKES.desc()
        q != null -> nameSimilarity(q).desc()
        else -> RECIPES.NAME.asc()
    }

    /**
     * Fuzzy-searches recipes by name and/or owner.
     *
     * @param q optional name filter - see [searchConditions]; when present and
     * [orderByLikes] is `false`, results are ranked by [nameSimilarity] descending
     * instead of alphabetically
     * @param userId optional owner filter
     * @param orderByLikes sorts by [rosemary_and_thyme.backend.jooqgenerated.public.tables.Recipes.LIKES]
     * descending instead - for browsing/discovering recipes across all users by
     * popularity, as opposed to [q]'s ordinary purpose of quickly relocating one
     * specific recipe you already know the name of (see
     * [rosemary_and_thyme.backend.controllers.UserController.searchOwnRecipes], the
     * only caller that leaves this at its default `false` - name relevance/alphabetical
     * order suits "my own handful of recipes" better than popularity does). Applies
     * whether or not [q] is given - [q] still narrows by name via the `%` condition
     * either way, only the *order* of the (possibly narrowed) results changes.
     * @return up to 100 matching recipes as (id, name) pairs - the lightweight shape
     * needed for a quick name lookup; [selectRecipeFeed] is the equivalent for a full
     * feed card
     */
    fun selectRecipesWithFilters(
        q: String? = null,
        userId: UUID? = null,
        orderByLikes: Boolean = false
    ): List<Pair<UUID, String>> =
        dsl.select(RECIPES.ID, RECIPES.NAME)
            .from(RECIPES)
            .where(searchConditions(q, userId))
            .orderBy(searchOrder(q, orderByLikes))
            .limit(100)
            .fetch { Pair(it[RECIPES.ID].dbNotNull("id"), it[RECIPES.NAME].dbNotNull("name")) }

    /**
     * Same fuzzy name/owner filtering and ordering as [selectRecipesWithFilters] (see
     * its doc for the full reasoning), but selects the full set of columns a feed card
     * needs (see [RecipeFeedDTO]) instead of just (id, name) - backs
     * [rosemary_and_thyme.backend.controllers.RecipeController.searchRecipes], the home
     * page's global recipe feed. Kept as a separate query rather than widening
     * [selectRecipesWithFilters] itself, since that one's only other caller
     * ([rosemary_and_thyme.backend.controllers.UserController.searchOwnRecipes]) only
     * ever needs the lightweight (id, name) shape - fetching every extra feed column
     * there would be wasted work for a "find one of my own recipes" lookup.
     *
     * [RecipeFeedDTO.heroImageUrl] is left `null` here on every row - a recipe can have
     * zero or several hero images, which doesn't fit this flat per-recipe row, so it's
     * resolved separately by [selectFirstHeroImages] once these ids are known (see
     * [rosemary_and_thyme.backend.services.recipes.RecipeService.searchRecipeFeed],
     * the only caller, which merges the two).
     *
     * @param q optional name filter, see [selectRecipesWithFilters]
     * @param userId optional owner filter
     * @param orderByLikes see [selectRecipesWithFilters]
     * @return up to 100 matching recipes as feed cards, hero image not yet resolved
     */
    fun selectRecipeFeed(
        q: String? = null,
        userId: UUID? = null,
        orderByLikes: Boolean = false
    ): List<RecipeFeedDTO> =
        dsl.select(
            RECIPES.ID, RECIPES.NAME, RECIPES.DESCRIPTION, RECIPES.LIKES,
            RECIPES.USER_NAME, RECIPES.CREATED_DATE
        )
            .from(RECIPES)
            .where(searchConditions(q, userId))
            .orderBy(searchOrder(q, orderByLikes))
            .limit(100)
            .fetch {
                RecipeFeedDTO(
                    id = it[RECIPES.ID].dbNotNull("id"),
                    name = it[RECIPES.NAME].dbNotNull("name"),
                    description = it[RECIPES.DESCRIPTION].dbNotNull("description"),
                    likes = it[RECIPES.LIKES].dbNotNull("likes"),
                    userName = it[RECIPES.USER_NAME].dbNotNull("user_name"),
                    createDate = it[RECIPES.CREATED_DATE].dbNotNull("created_date"),
                    heroImageUrl = null
                )
            }

    /**
     * First (lowest-index) hero image url for each of [recipeIds], one row per recipe
     * via Postgres `DISTINCT ON` - used to give each feed card a single preview image
     * without fetching/returning every hero image for every listed recipe.
     *
     * @param recipeIds recipes to fetch a preview image for, e.g. one page of
     * [selectRecipeFeed] results
     * @return recipeId -> that recipe's first hero image url; a recipe with no hero
     * image at all is simply absent from the map (see
     * [rosemary_and_thyme.backend.services.recipes.RecipeService.searchRecipeFeed],
     * which treats a missing entry as "no image")
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

    /** @return whether [userId] has already liked [recipeId] - backs the `liked` field on [RecipeDTO]. */
    fun hasUserLikedRecipe(recipeId: UUID, userId: UUID): Boolean =
        dsl.fetchExists(
            dsl.selectOne()
                .from(RECIPE_LIKES)
                .where(RECIPE_LIKES.RECIPE_ID.eq(recipeId))
                .and(RECIPE_LIKES.USER_ID.eq(userId))
        )

    /**
     * Records that [userId] likes [recipeId], if they don't already. `ON CONFLICT DO
     * NOTHING` (the composite PK on `recipe_likes` is the conflict target) makes this
     * safe against a duplicate/racing request re-liking an already-liked recipe -
     * without it, a second identical request would either throw a PK-violation
     * exception or (if checked-then-inserted non-atomically) double the counter via a
     * race between the existence check and the insert.
     *
     * @return `true` if a new like row was actually inserted (so the caller should
     * also bump [incrementLikes]), `false` if [userId] had already liked this recipe
     */
    fun insertLike(recipeId: UUID, userId: UUID): Boolean =
        dsl.insertInto(RECIPE_LIKES)
            .set(RECIPE_LIKES.RECIPE_ID, recipeId)
            .set(RECIPE_LIKES.USER_ID, userId)
            .onConflictDoNothing()
            .execute() == 1

    /**
     * Removes [userId]'s like on [recipeId], if one exists.
     *
     * @return `true` if a like row was actually deleted (so the caller should also
     * decrement [incrementLikes] with a negative delta), `false` if [userId] hadn't
     * liked this recipe in the first place
     */
    fun deleteLike(recipeId: UUID, userId: UUID): Boolean =
        dsl.deleteFrom(RECIPE_LIKES)
            .where(RECIPE_LIKES.RECIPE_ID.eq(recipeId))
            .and(RECIPE_LIKES.USER_ID.eq(userId))
            .execute() == 1

    /**
     * Adjusts a recipe's cached like counter. [delta] is `+1`/`-1` in practice (see
     * [rosemary_and_thyme.backend.services.recipes.RecipeService.likeRecipe]/`unlikeRecipe`),
     * applied as a single atomic `likes = likes + delta` update rather than a
     * read-modify-write from application code, so concurrent likes on the same recipe
     * can't lose an update to each other.
     */
    fun incrementLikes(recipeId: UUID, delta: Long) {
        dsl.update(RECIPES)
            .set(RECIPES.LIKES, RECIPES.LIKES.plus(delta))
            .where(RECIPES.ID.eq(recipeId))
            .execute()
    }

    /** @return the recipe's current like count, straight from the DB (not a caller-computed value). */
    fun selectLikesCount(recipeId: UUID): Long =
        dsl.select(RECIPES.LIKES)
            .from(RECIPES)
            .where(RECIPES.ID.eq(recipeId))
            .fetchOne(RECIPES.LIKES)
            .dbNotNull("likes")

}
