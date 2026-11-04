package rosemary_and_thyme.services.recipe_service

import org.jooq.JSONB
import rosemary_and_thyme.jooq.public.tables.references.RECIPES
import rosemary_and_thyme.models.exceptions.BadRecipeFormat
import rosemary_and_thyme.models.recipe.RecipeContentDTO
import rosemary_and_thyme.models.requests.PostRecipeRequest
import rosemary_and_thyme.models.user.UserPrincipal
import rosemary_and_thyme.services.RecipeService
import rosemary_and_thyme.services.Service.dsl
import java.time.OffsetDateTime

/**
 * Validates and stores a new recipe.
 * Ownership is derived from the authenticated principal rather than the request DTO.
 *
 * @param request recipe data supplied by the client
 * @param userPrincipal authenticated owner of the recipe
 * @param createdDate server-controlled recipe creation time
 * @throws BadRecipeFormat if the recipe fails validation
 */
fun RecipeService.postRecipe(
    request: PostRecipeRequest,
    userPrincipal: UserPrincipal,
) {

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

    dsl.insertInto(RECIPES)
        .set(RECIPES.TITLE, request.title)
        .set(RECIPES.DESCRIPTION, request.description)
        .set(RECIPES.SIDE_NOTES, request.sideNote)
        .set(RECIPES.DIFFICULTY, request.difficulty)
        .set(RECIPES.PORTIONS, request.portions)
        .set(RECIPES.CREATED_DATE, OffsetDateTime.now())
        .set(RECIPES.USER_ID, userPrincipal.id)
        .set(RECIPES.USER_NAME, userPrincipal.username)
        .set(RECIPES.CONTENT, contentJson)
        .execute()
        .also { affectedRows ->
            check(affectedRows == 1) {
                "Expected one inserted recipe, but inserted $affectedRows"
            }
        }

}


