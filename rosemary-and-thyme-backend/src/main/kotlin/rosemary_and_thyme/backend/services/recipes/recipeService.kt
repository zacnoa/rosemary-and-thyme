package rosemary_and_thyme.backend.services.recipes

import com.cloudinary.Cloudinary
import com.cloudinary.utils.ObjectUtils
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
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
import rosemary_and_thyme.backend.models.recipes.RecipeFeedDTO
import rosemary_and_thyme.backend.models.recipes.RecipeFeedPage
import rosemary_and_thyme.backend.models.recipes.RecipeWriteDTO
import rosemary_and_thyme.backend.repositories.dbNotNull
import rosemary_and_thyme.backend.repositories.recipes.RecipeRepository
import java.util.NoSuchElementException
import java.util.UUID

/**
 * Service layer.
 */

@Service
class RecipeService(
    private val recipeRepository: RecipeRepository,
    private val recipePersistenceService: RecipePersistenceService,
    @Value($$"${cloudinary.cloud-name}") private val cloudName: String,
    @Value($$"${cloudinary.api-key}") private val apiKey: String,
    @Value($$"${cloudinary.api-secret}") private val apiSecret: String
) {
    private val log = LoggerFactory.getLogger(RecipeService::class.java)

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
 * Deletes a collection of images from Cloudinary and logs failures.
 */
    private fun destroyCloudinaryImages(urls: Collection<String>) {
        urls.forEach { url ->
            val publicId = RecipeUtils.extractPublicId(url)
            for (attempt in 1..2) {
                try {
                    cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap())
                    return@forEach
                } catch (e: Exception) {
                    if (attempt == 2) log.warn("Failed to delete Cloudinary image {} after retry", publicId, e)
                }
            }
        }
    }

    /**
 * Fetches a recipe and assembles it into a [RecipeDTO].
 * @param id the recipe's id.
 * @param currentUserId the requesting caller's id, or `null` for an anonymous caller.
 * @return the assembled [RecipeDTO].
 * @throws NoSuchElementException if the recipe does not exist or is private to another user.
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
 * Waits for the async calls.
 */
        val recipe =
            recipeDeferred.await() ?: throw NoSuchElementException("The recipe you are looking for does not exist")

        // private recipes are invisible to everyone but their owner - same exception
        // as "doesn't exist" so a private id can't be distinguished from a bogus one.
        // .dbNotNull() (not `== true`) deliberately fails closed: is_private is NOT
        // NULL in the schema, so this can never actually be null, but if it somehow
        // were, this is a privacy gate - it should 500 loudly rather than silently
        // treat an unreadable value as "public".
        if (recipe.isPrivate.dbNotNull("isPrivate") && recipe.userId != currentUserId) {
            throw NoSuchElementException("The recipe you are looking for does not exist")
        }

        val ingredients = ingredientsDeferred.await()
        val instructions = instructionsDeferred.await()
        val heroImages = heroImagesDeferred.await()
        val liked = likedDeferred?.await() ?: false

        /**
 * We selectInstructionImages last because it needs to have access to instructionIds.
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
 * Adds a like for [user] and returns the updated like state.
 * @param recipeId the recipe to like.
 * @param user the authenticated caller.
 * @return the caller's new like state and the recipe's up-to-date like count.
 * @throws NoSuchElementException if no recipe exists with this id, or it's private and [user] isn't its owner.
 * @throws ForbiddenException if [user] is the recipe's own owner.
 */
    @Transactional
    fun likeRecipe(recipeId: UUID, user: UsersRecord): LikeStatusDTO {
        val recipe = recipeRepository.selectRecipeBase(recipeId)
            ?: throw NoSuchElementException("The recipe you are looking for does not exist")

        // fails closed on an unreadable value - see findRecipeById's identical check
        if (recipe.isPrivate.dbNotNull("isPrivate") && recipe.userId != user.id) {
            throw NoSuchElementException("The recipe you are looking for does not exist")
        }

        if (recipe.userId == user.id) {
            throw ForbiddenException("You cannot like your own recipe")
        }

        if (recipeRepository.insertLike(recipeId, user.id.dbNotNull("id"))) {
            recipeRepository.incrementLikes(recipeId, 1)
        }

        return LikeStatusDTO(liked = true, likes = recipeRepository.selectLikesCount(recipeId))
    }

    /**
 * Removes [user]'s like from a recipe. Repeating the operation is safe.
 * @param recipeId the recipe to unlike.
 * @param user the authenticated caller.
 * @return the caller's new like state and the recipe's up-to-date like count.
 * @throws NoSuchElementException if no recipe exists with this id.
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
 * Deletes a recipe and its associated images after checking ownership.
 * @param recipeId the recipe to delete.
 * @param user the authenticated caller.
 * @throws NoSuchElementException if no recipe exists with this id.
 * @throws ForbiddenException if [user] is not the recipe's owner.
 */
    fun deleteRecipe(recipeId: UUID, user: UsersRecord) {
        val recipe = recipeRepository.selectRecipeBase(recipeId)
            ?: throw NoSuchElementException("The recipe you are looking for does not exist")

        if (recipe.userId != user.id) {
            throw ForbiddenException("You are not the owner of this recipe")
        }

        val images = recipeRepository.selectAllImagesForRecipe(recipeId)
        recipePersistenceService.delete(recipeId)
        destroyCloudinaryImages(images.values)
    }

    /**
 * Deletes every recipe owned by [user], including associated remote images.
 * @param user the account being purged, also the owner checked by each [deleteRecipe] call.
 */
    fun deleteAllRecipesForUser(user: UsersRecord) {
        val recipeIds = recipeRepository.selectAllRecipeIds(user.id.dbNotNull("id"))
        recipeIds.forEach { deleteRecipe(it, user) }
    }

    /**
 * Updates a recipe's visibility after checking ownership.
 * @param recipeId the recipe to update.
 * @param isPrivate the new value.
 * @param user the authenticated caller, checked as the recipe's owner.
 * @throws NoSuchElementException if no recipe exists with this id.
 * @throws ForbiddenException if [user] is not the recipe's owner.
 */
    fun setRecipePrivate(recipeId: UUID, isPrivate: Boolean, user: UsersRecord) {
        val recipe = recipeRepository.selectRecipeBase(recipeId)
            ?: throw NoSuchElementException("The recipe you are looking for does not exist")

        if (recipe.userId != user.id) {
            throw ForbiddenException("You are not the owner of this recipe")
        }

        recipeRepository.updatePrivate(recipeId, isPrivate)
    }

    /**
 * Creates or updates a recipe and uploads its new images.
 * @param recipe the recipe's full desired state, as submitted by the client.
 * @param imageFiles new image files keyed by their temporary image IDs.
 * @param user the authenticated caller.
 * @return the saved recipe with persisted IDs and image URLs.
 * @throws rosemary_and_thyme.backend.exceptions.RecipeValidationException if [recipe] violates a [RecipeValidation] limit.
 * @throws ForbiddenException if [user] is not the owner of an existing recipe.
 */
    fun createRecipe(recipe: RecipeWriteDTO, imageFiles: Map<UUID, MultipartFile>, user: UsersRecord): RecipeDTO =
        saveNewRecipe(recipe, imageFiles, user)

    fun saveRecipe(
        recipeId: UUID,
        recipe: RecipeWriteDTO,
        imageFiles: Map<UUID, MultipartFile>,
        user: UsersRecord
    ): RecipeDTO = runBlocking {

        RecipeValidation.validate(recipe)

        val recipeBase = recipeRepository.selectRecipeBase(recipeId)
            ?: throw NoSuchElementException("The recipe you are looking for does not exist")
        if (recipeBase.userId != user.id) {
            throw ForbiddenException("You are not the owner of this recipe")
        }

        saveRecipeInternal(recipeId, recipe, imageFiles, user)
    }

    private fun saveNewRecipe(
        recipe: RecipeWriteDTO,
        imageFiles: Map<UUID, MultipartFile>,
        user: UsersRecord
    ): RecipeDTO = runBlocking {
        RecipeValidation.validate(recipe)
        saveRecipeInternal(null, recipe, imageFiles, user)
    }

    private suspend fun saveRecipeInternal(
        recipeId: UUID?,
        recipe: RecipeWriteDTO,
        imageFiles: Map<UUID, MultipartFile>,
        user: UsersRecord
    ): RecipeDTO {
        validateImageFiles(recipe, recipeId, imageFiles)

        val uploadedUrls = mutableListOf<Pair<UUID, String>>()

        return try {
            val newImages = recipe.images.filter { (id, img) ->
                img.url == null && imageFiles.containsKey(id)
            }

            // images the DB still has for this recipe that the client didn't send back -
            // i.e. what the user actually removed. Their urls only exist in this
            // "before" snapshot, never in the client's submitted `recipe.images` (which
            // by definition no longer contains them), so that map must not be used to
            // look them up.
            val existingImages = recipeId?.let { recipeRepository.selectAllImagesForRecipe(it) } ?: emptyMap()
            val deletedImages = existingImages.filterKeys { it !in recipe.images }

            val uploaded = withContext(Dispatchers.IO) {
                newImages.map { (tempId, _) ->
                    val file = imageFiles[tempId]!!
                    val result = cloudinary.uploader().upload(
                        file.bytes,
                        ObjectUtils.asMap("asset_folder", "Home/Recipes")
                    )
                    tempId to result["secure_url"].toString()
                }
            }

            uploadedUrls.addAll(uploaded)

            val uploadedImages = recipe.images.toMutableMap().apply {
                uploadedUrls.forEach { (tempId, url) ->
                    this[tempId] = this[tempId]!!.copy(url = url)
                }
            }

            val persistedId = recipePersistenceService.save(recipeId, recipe.copy(images = uploadedImages), user)

            // The DB transaction has committed. Only now can old remote files be removed.
            withContext(Dispatchers.IO) { destroyCloudinaryImages(deletedImages.values) }

            findRecipeById(persistedId, user.id)

        } catch (e: Exception) {
            /**
 * rollback for failed database save.
 */
            if (uploadedUrls.isNotEmpty()) {
                withContext(Dispatchers.IO) {
                    destroyCloudinaryImages(uploadedUrls.map { it.second })
                }
            }
            throw e
        }
    }

    /**
 * Persists a recipe (with already-uploaded image urls) across all of its tables in the associated behavior.
 * @param recipe the recipe to persist; [recipe.images][RecipeDTO.images] must.
 * @param user the recipe's owner, used only when inserting a brand-new recipe.
 * @return the recipe's id (existing id if updated, DB-generated id if newly inserted)
 */
    private fun validateImageFiles(recipe: RecipeWriteDTO, recipeId: UUID?, imageFiles: Map<UUID, MultipartFile>) {
        val existingImages = recipeId?.let { recipeRepository.selectAllImagesForRecipe(it) } ?: emptyMap()
        if (!imageFiles.keys.all { it in recipe.images }) {
            throw rosemary_and_thyme.backend.exceptions.RecipeValidationException("Every uploaded file must be declared by the recipe")
        }
        recipe.images.forEach { (id, image) ->
            val storedUrl = existingImages[id]
            if (storedUrl != null && image.url != storedUrl) {
                throw rosemary_and_thyme.backend.exceptions.RecipeValidationException("Existing image URLs cannot be changed")
            }
            if (storedUrl == null && (image.url != null || id !in imageFiles)) {
                throw rosemary_and_thyme.backend.exceptions.RecipeValidationException("New images must be uploaded as files")
            }
        }
        imageFiles.values.forEach { file ->
            if (file.contentType !in setOf("image/jpeg", "image/png")) {
                throw rosemary_and_thyme.backend.exceptions.RecipeValidationException("Only JPEG and PNG images are allowed")
            }
        }
    }

    /**
 * Same filtering/ordering as [searchRecipes], but returns full feed cards (see.
 * @param q optional fuzzy name filter, see [RecipeRepository.selectRecipeFeed].
 * @param userId optional owner filter.
 * @param orderByLikes see [RecipeRepository.selectRecipesWithFilters].
 * @param excludePrivate see [searchRecipes].
 * @param cursor see [RecipeRepository.selectRecipeFeed].
 * @return a page of matching recipes as feed cards, each with its first hero.
 */
    fun searchRecipeFeed(
        q: String? = null,
        userId: UUID? = null,
        orderByLikes: Boolean = false,
        excludePrivate: Boolean = false,
        cursor: String? = null
    ): RecipeFeedPage {
        val page = recipeRepository.selectRecipeFeed(q, userId, orderByLikes, excludePrivate, cursor)
        val heroImages = recipeRepository.selectFirstHeroImages(page.items.map { it.id })
        return page.copy(items = page.items.map { it.copy(heroImageUrl = heroImages[it.id]) })
    }

    /**
 * The dashboard's "Liked Recipes" section: every recipe [userId] has liked, most.
 * @param userId the user whose likes to list.
 * @param cursor see [RecipeRepository.selectLikedRecipeFeed].
 * @return a page of liked recipes as feed cards, most recently liked first, plus.
 */
    fun likedRecipeFeed(userId: UUID, cursor: String? = null): RecipeFeedPage {
        val page = recipeRepository.selectLikedRecipeFeed(userId, cursor)
        val heroImages = recipeRepository.selectFirstHeroImages(page.items.map { it.id })
        return page.copy(items = page.items.map { it.copy(heroImageUrl = heroImages[it.id]) })
    }

}
