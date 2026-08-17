package rosemary_and_thyme.backend.services.recipes

import com.cloudinary.Cloudinary
import com.cloudinary.utils.ObjectUtils
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import rosemary_and_thyme.backend.exceptions.ForbiddenException
import rosemary_and_thyme.backend.jooqgenerated.app_auth.tables.records.UsersRecord
import rosemary_and_thyme.backend.jooqgenerated.public.tables.records.IngredientsRecord
import rosemary_and_thyme.backend.jooqgenerated.public.tables.records.InstructionsRecord
import rosemary_and_thyme.backend.jooqgenerated.public.tables.records.RecipesRecord
import rosemary_and_thyme.backend.models.recipes.LikeStatusDTO
import rosemary_and_thyme.backend.models.recipes.RecipeDTO
import rosemary_and_thyme.backend.repositories.dbNotNull
import rosemary_and_thyme.backend.repositories.recipes.RecipeRepository
import java.util.NoSuchElementException
import java.util.UUID


/**
 * Service layer
 * This layer starts the async pipeline by declaring runBlocking in each function.
 *
 * runBlocking means that the Spring thread is blocked in the specific function until it finishes.
 * It also serves as a bridge between sequential and parallel execution of code
 */


//TODO: add separate POST method for creating recipes
@Service
class RecipeService(
    private val recipeRepository: RecipeRepository,
    @Value($$"${cloudinary.cloud-name}") private val cloudName: String,
    @Value($$"${cloudinary.api-key}") private val apiKey: String,
    @Value($$"${cloudinary.api-secret}") private val apiSecret: String
) {
    private val cloudinary by lazy {
        Cloudinary(
            mapOf(
                "cloud_name" to cloudName,
                "api_key" to apiKey,
                "api_secret" to apiSecret,
                "secure" to true
            )
        )
    }

    /**
     * Fetches a recipe and assembles it into a [RecipeDTO].
     *
     * A recipe is spread across four independent queries (base row, ingredients,
     * instructions, hero images) with no data dependency between them, so all four are
     * fired concurrently with [async]/[Dispatchers.IO] instead of run one after another -
     * wall-clock time is roughly the slowest single query rather than the sum of all
     * four. [instructionImages] is the exception: it needs the instruction ids from
     * [instructionsDeferred] to know what to fetch, so it can only start once that
     * result is in hand and runs as a plain (blocking) call afterward rather than a
     * fifth concurrent branch.
     *
     * @param id the recipe's id
     * @param currentUserId the requesting caller's id, or `null` for an anonymous
     * viewer - used only to resolve [RecipeDTO.liked]; when `null`, that lookup is
     * skipped entirely (an anonymous caller can never have liked anything) rather
     * than firing a query that would just resolve to `false`
     * @return the assembled [RecipeDTO]
     * @throws NoSuchElementException if no recipe exists with this id
     */
    fun findRecipeById(id: UUID, currentUserId: UUID? = null): RecipeDTO = runBlocking {

        val recipeDeferred: Deferred<RecipesRecord?> = async(Dispatchers.IO) { recipeRepository.selectRecipeBase(id) }
        val ingredientsDeferred: Deferred<List<IngredientsRecord>> =
            async(Dispatchers.IO) { recipeRepository.selectIngredients(id) }
        val instructionsDeferred: Deferred<List<InstructionsRecord>> =
            async(Dispatchers.IO) { recipeRepository.selectInstructions(id) }
        val heroImagesDeferred: Deferred<Map<UUID, String>> =
            async(Dispatchers.IO) { recipeRepository.selectHeroImages(id) }
        val likedDeferred: Deferred<Boolean>? = currentUserId?.let {
            async(Dispatchers.IO) { recipeRepository.hasUserLikedRecipe(id, it) }
        }

        /**
         * Waits for the async calls
         *
         * recipeDeffered throws NoSuchElementException when selectRecipeBase returns null(meaning no recipe by that id)
         */
        val recipe =
            recipeDeferred.await() ?: throw NoSuchElementException("The recipe you are looking for does not exist")
        val ingredients = ingredientsDeferred.await()
        val instructions = instructionsDeferred.await()
        val heroImages = heroImagesDeferred.await()
        val liked = likedDeferred?.await() ?: false

        /**
         * We selectInstructionImages last because it needs to have access to instructionIds
         */
        val instructionImages = recipeRepository.selectInstructionImages(instructions.mapNotNull { it.id })

        RecipeUtils.buildRecipeDTO(
            recipe = recipe,
            ingredients = ingredients,
            instructions = instructions,
            heroImages = heroImages,
            instructionImages = instructionImages,
            liked = liked
        )

    }

    /**
     * Likes a recipe on [user]'s behalf. A recipe's own owner can never like it - see
     * the class-level feature request this enforces: the frontend already never shows
     * a like button on your own recipe (owners get RecipeEditor, not Blog - see
     * routes/recipe/[id].tsx), but that's a UI convenience, not a security boundary,
     * so it's re-checked here against a direct API call.
     *
     * Idempotent: liking an already-liked recipe is a no-op (no double count, no
     * error) - see [RecipeRepository.insertLike].
     *
     * @param recipeId the recipe to like
     * @param user the authenticated caller
     * @return the caller's new like state and the recipe's up-to-date like count
     * @throws NoSuchElementException if no recipe exists with this id
     * @throws ForbiddenException if [user] is the recipe's own owner
     */
    @Transactional
    fun likeRecipe(recipeId: UUID, user: UsersRecord): LikeStatusDTO {
        val recipe = recipeRepository.selectRecipeBase(recipeId)
            ?: throw NoSuchElementException("The recipe you are looking for does not exist")

        if (recipe.userId == user.id) {
            throw ForbiddenException("You cannot like your own recipe")
        }

        if (recipeRepository.insertLike(recipeId, user.id.dbNotNull("id"))) {
            recipeRepository.incrementLikes(recipeId, 1)
        }

        return LikeStatusDTO(liked = true, likes = recipeRepository.selectLikesCount(recipeId))
    }

    /**
     * Removes [user]'s like from a recipe. Idempotent, same reasoning as [likeRecipe]:
     * unliking a recipe that was never liked (or already unliked - e.g. a duplicate
     * click/request) is a no-op rather than an error.
     *
     * @param recipeId the recipe to unlike
     * @param user the authenticated caller
     * @return the caller's new like state and the recipe's up-to-date like count
     * @throws NoSuchElementException if no recipe exists with this id
     */
    @Transactional
    fun unlikeRecipe(recipeId: UUID, user: UsersRecord): LikeStatusDTO {
        recipeRepository.selectRecipeBase(recipeId)
            ?: throw NoSuchElementException("The recipe you are looking for does not exist")

        if (recipeRepository.deleteLike(recipeId, user.id.dbNotNull("id"))) {
            recipeRepository.incrementLikes(recipeId, -1)
        }

        return LikeStatusDTO(liked = false, likes = recipeRepository.selectLikesCount(recipeId))
    }

    /**
     * Creates or updates a recipe together with its image files, keeping Cloudinary and
     * the database consistent with each other.
     *
     * Validation: [RecipeValidation.validate] runs first, before even the ownership
     * check below - it's a pure check on [recipe] itself (too many ingredients/steps,
     * description/title too long), so it can reject a bad submission without a single
     * DB query. See RecipeValidation's KDoc for why this exists in the first place:
     * it's the server-side twin of the frontend's own pre-save checks, and the one
     * that actually matters, since the frontend's is bypassable by calling this
     * endpoint directly.
     *
     * Ownership check: for an existing recipe, the authenticated [user] must be its
     * owner - this is the only authorization check in the save path, so it runs before
     * any upload/DB work starts.
     *
     * Image upload/delete workflow (why this function is the most involved one in the
     * service):
     * 1. [newImages] - entries in [recipe.images][RecipeDTO.images] that have no `url`
     *    yet but do have a matching file in [imageFiles] - are new images the client
     *    just added.
     * 2. [deletedImages] is computed by diffing the DB's current image set
     *    ([RecipeRepository.selectAllImagesForRecipe]) against what the client submitted: any
     *    image the DB has that [recipe.images][RecipeDTO.images] no longer mentions is
     *    something the user removed. This "before" snapshot is the only place those
     *    urls still exist, since by definition the client's submitted map no longer
     *    contains them.
     * 3. Uploads (to Cloudinary) and deletes run concurrently via [async], since
     *    neither depends on the other's result.
     * 4. Once uploads finish, [recipe.images][RecipeDTO.images] is patched with the
     *    real Cloudinary urls for the temp-id-keyed new images, and the result is
     *    persisted via [saveRecipeAfterImageUpload].
     * 5. **Rollback on failure**: if anything throws after images were already
     *    uploaded (most likely the DB save in [saveRecipeAfterImageUpload]), every
     *    image uploaded in this call is deleted from Cloudinary in the `catch` block
     *    before the exception is rethrown - otherwise a failed save would still leave
     *    orphaned files sitting in Cloudinary storage.
     *
     * @param recipe the recipe's full desired state, as submitted by the client
     * @param imageFiles new image files from the multipart request, keyed by the
     * temporary [UUID] the client assigned to that image
     * @param user the authenticated caller
     * @return the saved recipe, re-fetched via [findRecipeById] so the response
     * reflects the final DB/Cloudinary state (real ids, uploaded urls, etc.)
     * @throws rosemary_and_thyme.backend.exceptions.RecipeValidationException if [recipe] violates a [RecipeValidation] limit
     * @throws ForbiddenException if [user] is not the owner of an existing recipe
     */
    fun saveRecipe(
        recipe: RecipeDTO,
        imageFiles: Map<UUID, MultipartFile>,
        user: UsersRecord
    ): RecipeDTO = runBlocking {

        RecipeValidation.validate(recipe)

        val recipeBase = recipeRepository.selectRecipeBase(recipe.id)

        if (recipeBase != null && recipeBase.userId != user.id) {
            throw ForbiddenException("You are not the owner of this recipe")
        }


        val uploadedUrls = mutableListOf<Pair<UUID, String>>()

        try {
            val newImages = recipe.images.filter { (id, img) ->
                img.url == null && imageFiles.containsKey(id)
            }

            // images the DB still has for this recipe that the client didn't send back -
            // i.e. what the user actually removed. Their urls only exist in this
            // "before" snapshot, never in the client's submitted `recipe.images` (which
            // by definition no longer contains them), so that map must not be used to
            // look them up.
            val existingImages = recipeRepository.selectAllImagesForRecipe(recipe.id)
            val deletedImages = existingImages.filterKeys { it !in recipe.images }

            val uploadDeferred = async(Dispatchers.IO) {
                newImages.map { (tempId, _) ->
                    val file = imageFiles[tempId]!!
                    val result = cloudinary.uploader().upload(
                        file.bytes,
                        ObjectUtils.asMap("asset_folder", "Home/Recipes")
                    )
                    tempId to result["secure_url"].toString()
                }
            }

            val deleteDeferred = async(Dispatchers.IO) {
                deletedImages.values.forEach { url ->
                    cloudinary.uploader().destroy(RecipeUtils.extractPublicId(url), ObjectUtils.emptyMap())
                }
            }

            uploadedUrls.addAll(uploadDeferred.await())
            deleteDeferred.await()

            val uploadedImages = recipe.images.toMutableMap().apply {
                uploadedUrls.forEach { (tempId, url) ->
                    this[tempId] = this[tempId]!!.copy(url = url)
                }
            }

            val recipeId = saveRecipeAfterImageUpload(recipe.copy(images = uploadedImages), user)

            findRecipeById(recipeId)

        } catch (e: Exception) {
            /**
             * rollback for failed database save
             */
            if (uploadedUrls.isNotEmpty()) {
                withContext(Dispatchers.IO) {
                    uploadedUrls.forEach { (_, url) ->
                        val publicId = RecipeUtils.extractPublicId(url)
                        cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap())
                    }
                }
            }
            throw e
        }
    }

    /**
     * Persists a recipe (with already-uploaded image urls) across all of its tables in
     * one transaction, so a partial write (e.g. base row saved but ingredients fail)
     * can never be observed.
     *
     * @param recipe the recipe to persist; [recipe.images][RecipeDTO.images] must
     * already contain real urls (see [saveRecipe], which patches them in before calling this)
     * @param user the recipe's owner, used only when inserting a brand-new recipe
     * (see [RecipeRepository.upsertRecipeBase])
     * @return the recipe's id (existing id if updated, DB-generated id if newly inserted)
     */
    @Transactional
    fun saveRecipeAfterImageUpload(recipe: RecipeDTO, user: UsersRecord): UUID {
        /**
         * This call is not async because before we call any other query we need the real id of the recipe
         */
        val recipeId = recipeRepository.upsertRecipeBase(recipe, user)
        recipeRepository.syncIngredients(recipeId, recipe.ingredients, recipe.ingredientsOrder)
        recipeRepository.syncHeroImages(recipeId, recipe.images, recipe.heroImagesOrder)
        recipeRepository.syncInstructions(recipeId, recipe.instructions, recipe.instructionsOrder, recipe.images)
        return recipeId
    }

    /**
     * @param q optional fuzzy name filter (see [RecipeRepository.selectRecipesWithFilters])
     * @param userId optional owner filter
     * @param orderByLikes see [RecipeRepository.selectRecipesWithFilters]
     * @return matching recipes, capped at 100, as (id, name) pairs
     */
    fun searchRecipes(q: String? = null, userId: UUID? = null, orderByLikes: Boolean = false): List<Pair<UUID, String>> =

        recipeRepository.selectRecipesWithFilters(q, userId, orderByLikes)

}
