package rosemary_and_thyme.backend.services

import com.cloudinary.Cloudinary
import com.cloudinary.utils.ObjectUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import rosemary_and_thyme.backend.models.ApiResponse
import rosemary_and_thyme.backend.repositories.recipes.RecipeDTO
import rosemary_and_thyme.backend.repositories.recipes.RecipeRepository
import rosemary_and_thyme.backend.repositories.recipes.fetchRecipe
import rosemary_and_thyme.backend.repositories.recipes.saveRecipe
import java.util.UUID

@Service
class RecipeService(
    private val recipeRepository: RecipeRepository,
    @Value($$"${cloudinary.cloud-name}") private val cloudName: String,
    @Value($$"${cloudinary.api-key}") private val apiKey: String,
    @Value($$"${cloudinary.api-secret}") private val apiSecret: String
)
{

    private val cloudinary by lazy {
        Cloudinary(mapOf(
            "cloud_name" to cloudName,
            "api_key" to apiKey,
            "api_secret" to apiSecret,
            "secure" to true
        ))
    }

    fun findRecipeById(id: UUID): ApiResponse<RecipeDTO> =
        recipeRepository.fetchRecipe(id)

    fun saveRecipe(
        recipe: RecipeDTO,
        imageFiles: Map<UUID, MultipartFile>
    ): ApiResponse<RecipeDTO> = runBlocking {
        val newImages = recipe.images.filter { (id, img) ->
            img.url == null && imageFiles.containsKey(id)
        }
        val existingHeroIds = recipeRepository.fetchHeroImageIds(recipe.id)
        val existingInstructionImageIds = recipeRepository.fetchAllInstructionImageIds(recipe.id)
        val allExistingIds = existingHeroIds + existingInstructionImageIds
        val deletedIds = allExistingIds - recipe.images.keys

        val uploadDeferred = async(Dispatchers.IO) {
            newImages.map { (tempId, _) ->
                val file = imageFiles[tempId]!!
                val result = cloudinary.uploader().upload(
                    file.bytes,
                    ObjectUtils.asMap("folder", "recipes")
                )
                tempId to result["secure_url"].toString()
            }
        }
        val deleteDeferred = async(Dispatchers.IO) {
            deletedIds.forEach { id ->
                cloudinary.uploader().destroy(id.toString(), ObjectUtils.emptyMap())
            }
        }

        val uploadedUrls = uploadDeferred.await()
        deleteDeferred.await()

        val updatedImages = recipe.images.toMutableMap()
        uploadedUrls.forEach { (tempId, url) ->
            updatedImages[tempId] = updatedImages[tempId]!!.copy(url = url)
        }

        recipeRepository.saveRecipe(recipe.copy(images = updatedImages))
        recipeRepository.fetchRecipe(recipe.id)
    }
}
