package rosemary_and_thyme.services.recipe_service

import org.jooq.JSONB
import rosemary_and_thyme.jooq.public.tables.references.RECIPES
import rosemary_and_thyme.models.exceptions.BadRecipeFormat
import rosemary_and_thyme.models.recipe.RecipeContentDTO
import rosemary_and_thyme.models.recipe.RecipeDTO
import rosemary_and_thyme.models.user.UserPrincipal
import rosemary_and_thyme.services.RecipeService
import rosemary_and_thyme.services.Service.dsl
import java.time.OffsetDateTime

/**
 * Validates and stores a new recipe.
 * Ownership is derived from the authenticated principal rather than the request DTO.
 *
 * @param recipeDTO recipe data supplied by the client
 * @param userPrincipal authenticated owner of the recipe
 * @param createdDate server-controlled recipe creation time
 * @throws BadRecipeFormat if the recipe fails validation
 */
fun RecipeService.postRecipe(
    recipeDTO: RecipeDTO,
    userPrincipal: UserPrincipal,
    createdDate: OffsetDateTime = OffsetDateTime.now()
) {

    val errorMessages = validateRecipe(recipeDTO)
    if (errorMessages.isNotEmpty()) {
        throw BadRecipeFormat(message = errorMessages.joinToString(separator = "\n"))
    }

    val content = RecipeContentDTO(
        images = recipeDTO.images,
        ingredients = recipeDTO.ingredients,
        instructions = recipeDTO.instructions,
        ingredientsOrder = recipeDTO.ingredientsOrder,
        instructionsOrder = recipeDTO.instructionsOrder,
        heroImagesOrder = recipeDTO.heroImagesOrder
    )

    val contentJson = JSONB.jsonb(
        serializeRecipeContent(content)
    )

    dsl.insertInto(RECIPES)
        .set(RECIPES.TITLE, recipeDTO.title)
        .set(RECIPES.DESCRIPTION, recipeDTO.description)
        .set(RECIPES.SIDE_NOTES, recipeDTO.sideNote)
        .set(RECIPES.DIFFICULTY, recipeDTO.difficulty)
        .set(RECIPES.PORTIONS, recipeDTO.portions)
        .set(RECIPES.CREATED_DATE, createdDate)
        .set(RECIPES.USER_ID, userPrincipal.id)
        .set(RECIPES.USER_NAME, userPrincipal.username)
        .set(RECIPES.IS_PRIVATE, recipeDTO.isPrivate)
        .set(RECIPES.CONTENT, contentJson)
        .execute()
        .also { affectedRows ->
            check(affectedRows == 1) {
                "Expected one inserted recipe, but inserted $affectedRows"
            }
        }

}


