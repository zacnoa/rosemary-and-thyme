package rosemary_and_thyme.backend.services.recipes

import com.cloudinary.Cloudinary
import com.cloudinary.utils.ObjectUtils
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import rosemary_and_thyme.backend.jooqgenerated.public.tables.records.IngredientsRecord
import rosemary_and_thyme.backend.jooqgenerated.public.tables.records.InstructionsRecord
import rosemary_and_thyme.backend.jooqgenerated.public.tables.records.RecipesRecord
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

    fun findRecipeById(id: UUID): RecipeDTO = runBlocking {

        val recipeDeferred: Deferred<RecipesRecord?> = async(Dispatchers.IO) { recipeRepository.fetchRecipeBase(id) }
        val ingredientsDeferred: Deferred<List<IngredientsRecord>> =
            async(Dispatchers.IO) { recipeRepository.fetchIngredients(id) }
        val instructionsDeferred: Deferred<List<InstructionsRecord>> =
            async(Dispatchers.IO) { recipeRepository.fetchInstructions(id) }
        val heroImagesDeferred: Deferred<Map<UUID, String>> =
            async(Dispatchers.IO) { recipeRepository.fetchHeroImages(id) }

        /**
         * Waits for the async calls
         *
         * recipeDeffered throws NoSuchElementException when fetchRecipeBase returns null(meaning no recipe by that id)
         */
        val recipe =
            recipeDeferred.await() ?: throw NoSuchElementException("The recipe you are looking for does not exist")
        val ingredients = ingredientsDeferred.await()
        val instructions = instructionsDeferred.await()
        val heroImages = heroImagesDeferred.await()

        /**
         * We fetchInstructionImages last because it needs to have access to instructionIds
         */
        val instructionImages = recipeRepository.fetchInstructionImages(instructions.mapNotNull { it.id })

        RecipeUtils.buildRecipeDTO(
            recipe = recipe,
            ingredients = ingredients,
            instructions = instructions,
            heroImages = heroImages,
            instructionImages = instructionImages
        )

    }

    fun saveRecipe(recipe: RecipeDTO, imageFiles: Map<UUID, MultipartFile>): RecipeDTO = runBlocking {

        val uploadedUrls = mutableListOf<Pair<UUID, String>>()

        try {
            val newImages = recipe.images.filter { (id, img) ->
                img.url == null && imageFiles.containsKey(id)
            }

            val allImages = recipeRepository.fetchAllImageIds(recipe.id)
            val deletedIds = allImages - recipe.images.keys

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
                deletedIds.forEach { id ->
                    val publicId = RecipeUtils.extractPublicId(recipe.images[id]?.url!!)
                    cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap())
                }
            }

            uploadedUrls.addAll(uploadDeferred.await())
            deleteDeferred.await()

            val uploadedImages = recipe.images.toMutableMap().apply {
                uploadedUrls.forEach { (tempId, url) ->
                    this[tempId] = this[tempId]!!.copy(url = url)
                }
            }

            saveRecipeAfterImageUpload(recipe.copy(images = uploadedImages))

            findRecipeById(recipe.id)

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

    @Transactional
    fun saveRecipeAfterImageUpload(recipe: RecipeDTO) {
        /**
         * This call is not async because before we call any other query we need the real id of the recipe
         */
        val recipeId = recipeRepository.saveRecipeBase(recipe)
        recipeRepository.saveIngredients(recipeId, recipe.ingredients, recipe.ingredientsOrder)
        recipeRepository.saveHeroImages(recipeId, recipe.images, recipe.heroImagesOrder)
        recipeRepository.saveInstructions(recipeId, recipe.instructions, recipe.instructionsOrder, recipe.images)
    }

}
