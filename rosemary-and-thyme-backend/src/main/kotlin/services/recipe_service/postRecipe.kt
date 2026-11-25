package rosemary_and_thyme.services.recipe_service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jooq.JSONB
import rosemary_and_thyme.jooq.public.tables.references.RECIPES
import rosemary_and_thyme.models.exceptions.BadRecipeFormat
import rosemary_and_thyme.models.responses.RecipeContentDTO
import rosemary_and_thyme.models.requests.PostRecipeRequest
import rosemary_and_thyme.models.responses.PostRecipeResponse
import rosemary_and_thyme.models.user.UserPrincipal
import rosemary_and_thyme.services.RecipeService
import rosemary_and_thyme.services.Service
import java.time.OffsetDateTime

/**
 * Validates and stores a new draft recipe with its complete intended content.
 * Ownership is derived from the authenticated principal, and the database generates the recipe ID.
 *
 * @param request recipe data supplied by the client
 * @param userPrincipal authenticated owner of the recipe
 * @return generated identifier of the stored draft
 * @throws BadRecipeFormat if the recipe fails validation
 * @throws IllegalStateException if a required shared service has not been initialized
 */
suspend fun RecipeService.postRecipe(
    request: PostRecipeRequest,
    userPrincipal: UserPrincipal,
): PostRecipeResponse {

    val errorMessages = validateRecipe(request)
    if (errorMessages.isNotEmpty()) {
        throw BadRecipeFormat(message = errorMessages.joinToString(separator = "\n"))
    }

    val content = RecipeContentDTO(
        imageIds = request.imageIds,
        ingredients = request.ingredients,
        instructions = request.instructions,
        ingredientsOrder = request.ingredientsOrder,
        instructionsOrder = request.instructionsOrder,
        heroImagesOrder = request.heroImagesOrder
    )

    val contentJson = JSONB.jsonb(
        serializeRecipeContent(content)
    )

    //TODO("better  handling of DataAccessException")
    val recipeId = withContext(Dispatchers.IO) {
        checkNotNull(
            Service.dsl.insertInto(RECIPES)
                .set(RECIPES.TITLE, request.title)
                .set(RECIPES.DESCRIPTION, request.description)
                .set(RECIPES.SIDE_NOTES, request.sideNote)
                .set(RECIPES.DIFFICULTY, request.difficulty)
                .set(RECIPES.PORTIONS, request.portions)
                .set(RECIPES.COOK_TIME, request.cookTime)
                .set(RECIPES.CREATED_DATE, OffsetDateTime.now())
                .set(RECIPES.USER_ID, userPrincipal.id)
                .set(RECIPES.USER_NAME, userPrincipal.username)
                .set(RECIPES.CONTENT, contentJson)
                .returning(RECIPES.ID)
                .fetchSingle()
                .get(RECIPES.ID)
        ) {
            "The database did not return an id for the inserted recipe"
        }
    }
    
    return PostRecipeResponse(recipeId = recipeId.toString())

}


