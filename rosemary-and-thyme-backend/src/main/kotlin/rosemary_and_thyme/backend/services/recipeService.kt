package rosemary_and_thyme.backend.services

import com.cloudinary.Cloudinary
import com.cloudinary.utils.ObjectUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import rosemary_and_thyme.backend.models.ApiResponse
import rosemary_and_thyme.backend.repositories.recipes.RecipeDTO
import rosemary_and_thyme.backend.repositories.recipes.RecipeRepository
import rosemary_and_thyme.backend.repositories.recipes.fetchRecipe
import rosemary_and_thyme.backend.repositories.recipes.saveRecipe
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
        Cloudinary(mapOf(
            "cloud_name" to cloudName,
            "api_key" to apiKey,
            "api_secret" to apiSecret,
            "secure" to true
        ))
    }

    fun findRecipeById(id: UUID): ApiResponse<RecipeDTO> = runBlocking {
        recipeRepository.fetchRecipe(id)
    }

    fun saveRecipe(recipe: RecipeDTO, imageFiles: Map<UUID, MultipartFile>): ApiResponse<RecipeDTO> = runBlocking {

        val uploadedUrls = mutableListOf<Pair<UUID, String>>()

        try {
            val newImages = recipe.images.filter { (id, img) ->
                img.url == null && imageFiles.containsKey(id)
            }

            val allImages=recipeRepository.fetchAllImageIds(recipe.id)
            val deletedIds =  allImages- recipe.images.keys

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
                    val publicId=ServiceUtils.extractPublicId(recipe.images[id]?.url!!)
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

            recipeRepository.saveRecipe(recipe.copy(images = uploadedImages))
            recipeRepository.fetchRecipe(recipe.id)

        } catch (e: Exception) {
            /**
             * rollback for failed database save
             */
            if (uploadedUrls.isNotEmpty()) {
                withContext(Dispatchers.IO) {
                    uploadedUrls.forEach { (_, url) ->
                        val publicId = ServiceUtils.extractPublicId(url)
                        cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap())
                    }
                }
            }
            throw e
        }
    }
}
