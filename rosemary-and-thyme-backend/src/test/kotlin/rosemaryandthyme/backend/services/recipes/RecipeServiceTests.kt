package rosemaryandthyme.backend.services.recipes

import com.cloudinary.Cloudinary
import com.cloudinary.Uploader
import com.cloudinary.utils.ObjectUtils
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions
import org.mockito.Mockito.`when`
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.util.ReflectionTestUtils
import rosemaryandthyme.backend.jooqgenerated.app_auth.tables.records.UsersRecord
import rosemaryandthyme.backend.models.recipes.RecipeImageDTO
import rosemaryandthyme.backend.models.recipes.RecipeWriteDTO
import rosemaryandthyme.backend.repositories.recipes.RecipeRepository
import rosemaryandthyme.backend.services.ratelimit.RateLimiterService
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class RecipeServiceTests {
    private val repository = mock(RecipeRepository::class.java)
    private val persistence = mock(RecipePersistenceService::class.java)
    private val cloudinary = mock(Cloudinary::class.java)
    private val uploader = mock(Uploader::class.java)
    private val service = RecipeService(
        repository, persistence, mock(RateLimiterService::class.java), "test", "test", "test"
    )
    private val user = UsersRecord(id = UUID.randomUUID(), username = "test")
    private val imageId = UUID.randomUUID()
    private val file = MockMultipartFile(imageId.toString(), "image.jpg", "image/jpeg", byteArrayOf(1, 2, 3))
    private val uploadedUrl = "https://res.cloudinary.com/test/image/upload/v1/recipe-image.jpg"
    private val recipe = RecipeWriteDTO(
        name = "Test recipe",
        description = "",
        portions = 1,
        cookTime = "",
        difficulty = 1,
        sideNotes = null,
        images = mapOf(imageId to RecipeImageDTO(imageId, null)),
        ingredients = emptyMap(),
        instructions = emptyMap(),
        ingredientsOrder = emptyList(),
        instructionsOrder = emptyList(),
        heroImagesOrder = listOf(imageId)
    )
    private val uploadedRecipe = recipe.copy(images = mapOf(imageId to RecipeImageDTO(imageId, uploadedUrl)))

    init {
        ReflectionTestUtils.setField(service, "cloudinary\$delegate", lazyOf(cloudinary))
        `when`(cloudinary.uploader()).thenReturn(uploader)
        `when`(uploader.upload(file.bytes, ObjectUtils.asMap("asset_folder", "Home/Recipes")))
            .thenReturn(mapOf("secure_url" to uploadedUrl))
    }

    @Test
    fun `does not delete uploaded images when reading the committed recipe fails`() {
        val recipeId = UUID.randomUUID()
        `when`(persistence.save(null, uploadedRecipe, user, false)).thenReturn(recipeId)
        // A missing recipe on the subsequent read throws after persistence succeeded.
        `when`(repository.selectRecipeBase(recipeId)).thenReturn(null)

        assertFailsWith<NoSuchElementException> {
            service.createRecipe(recipe, mapOf(imageId to file), user)
        }

        verify(persistence).save(null, uploadedRecipe, user, false)
        verify(uploader).upload(file.bytes, ObjectUtils.asMap("asset_folder", "Home/Recipes"))
        verifyNoMoreInteractions(uploader)
    }

    @Test
    fun `deletes uploaded images when database persistence fails`() {
        val failure = IllegalStateException("Database save failed")
        `when`(persistence.save(null, uploadedRecipe, user, false)).thenThrow(failure)

        val thrown = assertFailsWith<IllegalStateException> {
            service.createRecipe(recipe, mapOf(imageId to file), user)
        }

        assertSame(failure, thrown)
        verify(uploader).destroy("recipe-image", ObjectUtils.emptyMap())
    }
}
