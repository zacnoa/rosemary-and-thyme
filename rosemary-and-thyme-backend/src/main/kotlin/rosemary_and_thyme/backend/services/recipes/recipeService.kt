package rosemary_and_thyme.backend.services.recipes

import com.cloudinary.Cloudinary
import com.cloudinary.utils.ObjectUtils
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import rosemary_and_thyme.backend.exceptions.ForbiddenException
import rosemary_and_thyme.backend.exceptions.RecipeValidationException
import rosemary_and_thyme.backend.jooqgenerated.app_auth.tables.records.UsersRecord
import rosemary_and_thyme.backend.jooqgenerated.public.tables.records.IngredientsRecord
import rosemary_and_thyme.backend.jooqgenerated.public.tables.records.InstructionsRecord
import rosemary_and_thyme.backend.jooqgenerated.public.tables.records.RecipesRecord
import rosemary_and_thyme.backend.models.recipes.LikeStatusDTO
import rosemary_and_thyme.backend.models.recipes.RecipeDTO
import rosemary_and_thyme.backend.models.recipes.RecipeFeedPage
import rosemary_and_thyme.backend.models.recipes.RecipeWriteDTO
import rosemary_and_thyme.backend.repositories.dbNotNull
import rosemary_and_thyme.backend.repositories.recipes.RecipeRepository
import java.util.*

/** Coordinates recipe retrieval, persistence, images, feeds, and likes. */

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

    /** Deletes remote Cloudinary images and logs failed attempts.
     * @param urls the delivery URLs to delete.
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

    /** Fetches a recipe and assembles its complete DTO.
     * @param id the recipe id.
     * @param currentUserId the optional requesting user id.
     * @return the assembled recipe.
     * @throws NoSuchElementException if the recipe is missing or private to another user.
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

    /** Adds a user's like to a recipe.
     * @param recipeId the recipe to like.
     * @param user the authenticated caller.
     * @return the updated like state and count.
     * @throws ForbiddenException if the caller owns the recipe.
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

    /** Removes a user's like from a recipe.
     * @param recipeId the recipe to unlike.
     * @param user the authenticated caller.
     * @return the updated like state and count.
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

    /** Deletes a recipe and its remote images.
     * @param recipeId the recipe to delete.
     * @param user the authenticated owner.
     * @throws ForbiddenException if the caller does not own the recipe.
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

    /** Deletes every recipe owned by a user.
     * @param user the account whose recipes are deleted.
     */
    fun deleteAllRecipesForUser(user: UsersRecord) {
        val recipeIds = recipeRepository.selectAllRecipeIds(user.id.dbNotNull("id"))
        recipeIds.forEach { deleteRecipe(it, user) }
    }

    /** Updates a recipe's visibility.
     * @param recipeId the recipe to update.
     * @param isPrivate the requested visibility.
     * @param user the authenticated owner.
     * @throws ForbiddenException if the caller does not own the recipe.
     */
    fun setRecipePrivate(recipeId: UUID, isPrivate: Boolean, user: UsersRecord) {
        val recipe = recipeRepository.selectRecipeBase(recipeId)
            ?: throw NoSuchElementException("The recipe you are looking for does not exist")

        if (recipe.userId != user.id) {
            throw ForbiddenException("You are not the owner of this recipe")
        }

        recipeRepository.updatePrivate(recipeId, isPrivate)
    }

    /** Updates an existing recipe and uploads its new images.
     * @param recipeId the recipe to update.
     * @param recipe the complete submitted recipe state.
     * @param imageFiles uploaded files keyed by temporary image id.
     * @param user the authenticated owner.
     * @return the persisted recipe.
     * @throws ForbiddenException if the caller does not own the recipe.
     */

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

    /** Creates a recipe and uploads its images.
     * @param recipe the complete submitted recipe state.
     * @param imageFiles uploaded files keyed by temporary image id.
     * @param user the authenticated owner.
     * @return the persisted recipe.
     */
    fun createRecipe(recipe: RecipeWriteDTO, imageFiles: Map<UUID, MultipartFile>, user: UsersRecord): RecipeDTO {
        RecipeValidation.validate(recipe)
        return saveRecipeInternal(null,recipe,imageFiles,user)
    }

    private  fun saveRecipeInternal(
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

            val uploaded = newImages.map { (tempId, _) ->
                    val file = imageFiles[tempId]!!
                    val result = cloudinary.uploader().upload(
                        file.bytes,
                        ObjectUtils.asMap("asset_folder", "Home/Recipes")
                    )
                    tempId to result["secure_url"].toString()
                }


            uploadedUrls.addAll(uploaded)

            val uploadedImages = recipe.images.toMutableMap().apply {
                uploadedUrls.forEach { (tempId, url) ->
                    this[tempId] = this[tempId]!!.copy(url = url)
                }
            }

            val persistedId = recipePersistenceService.save(recipeId, recipe.copy(images = uploadedImages), user)

            // The DB transaction has committed. Only now can old remote files be removed.
             destroyCloudinaryImages(deletedImages.values)

            findRecipeById(persistedId, user.id)

        } catch (e: Exception) {
            /** rollback for failed database save. */
            if (uploadedUrls.isNotEmpty()) {
               destroyCloudinaryImages(uploadedUrls.map { it.second })
            }
            throw e
        }
    }

    /** Validates image metadata and multipart files before saving.
     * @param recipe the submitted recipe state.
     * @param recipeId the existing recipe id, or `null` for a new recipe.
     * @param imageFiles uploaded files keyed by temporary image id.
     * @throws RecipeValidationException if image metadata or formats are invalid.
     */
    private fun validateImageFiles(recipe: RecipeWriteDTO, recipeId: UUID?, imageFiles: Map<UUID, MultipartFile>) {
        val existingImages = recipeId?.let { recipeRepository.selectAllImagesForRecipe(it) } ?: emptyMap()
        if (!imageFiles.keys.all { it in recipe.images }) {
            throw RecipeValidationException("Every uploaded file must be declared by the recipe")
        }
        recipe.images.forEach { (id, image) ->
            val storedUrl = existingImages[id]
            if (storedUrl != null && image.url != storedUrl) {
                throw RecipeValidationException("Existing image URLs cannot be changed")
            }
            if (storedUrl == null && (image.url != null || id !in imageFiles)) {
                throw RecipeValidationException("New images must be uploaded as files")
            }
        }
        imageFiles.values.forEach { file ->
            if (file.contentType !in setOf("image/jpeg", "image/png")) {
                throw RecipeValidationException("Only JPEG and PNG images are allowed")
            }
        }
    }

    /** Returns one filtered and ordered recipe-feed page.
     * @param q the optional fuzzy name query.
     * @param userId the optional owner filter.
     * @param orderByLikes whether to sort by likes.
     * @param excludePrivate whether to omit private recipes.
     * @param cursor the optional previous-page cursor.
     * @return a recipe-feed page with hero images.
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

    /** Returns one page of recipes liked by a user.
     * @param userId the user whose likes are listed.
     * @param cursor the optional previous-page cursor.
     * @return a liked-recipe feed page with hero images.
     */
    fun likedRecipeFeed(userId: UUID, cursor: String? = null): RecipeFeedPage {
        val page = recipeRepository.selectLikedRecipeFeed(userId, cursor)
        val heroImages = recipeRepository.selectFirstHeroImages(page.items.map { it.id })
        return page.copy(items = page.items.map { it.copy(heroImageUrl = heroImages[it.id]) })
    }

}
