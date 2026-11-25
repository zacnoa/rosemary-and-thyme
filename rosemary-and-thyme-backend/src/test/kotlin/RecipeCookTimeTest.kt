package rosemary_and_thyme

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import rosemary_and_thyme.models.requests.PostRecipeRequest
import rosemary_and_thyme.models.requests.PutRecipeRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RecipeCookTimeTest {
    private val recipeFields = """
        "title": "Lasagna",
        "description": "",
        "portions": 4,
        "difficulty": 2,
        "sideNote": "",
        "imageIds": [],
        "ingredients": {},
        "instructions": {},
        "ingredientsOrder": [],
        "instructionsOrder": [],
        "heroImagesOrder": []
    """.trimIndent()

    @Test
    fun `create and update accept cook time as text`() {
        val create = Json.decodeFromString<PostRecipeRequest>("""{$recipeFields, "cookTime": "2h 30m"}""")
        val update = Json.decodeFromString<PutRecipeRequest>(
            """{$recipeFields, "cookTime": "2h 30m", "isPrivate": false, "version": 1}"""
        )

        assertEquals("2h 30m", create.cookTime)
        assertEquals("2h 30m", update.cookTime)
    }

    @Test
    fun `create and update require cook time`() {
        assertFailsWith<SerializationException> {
            Json.decodeFromString<PostRecipeRequest>("{$recipeFields}")
        }
        assertFailsWith<SerializationException> {
            Json.decodeFromString<PutRecipeRequest>("""{$recipeFields, "isPrivate": false, "version": 1}""")
        }
    }
}
