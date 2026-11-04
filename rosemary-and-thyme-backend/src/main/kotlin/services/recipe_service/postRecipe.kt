package rosemary_and_thyme.services.recipe_service

import org.jooq.JSONB
import rosemary_and_thyme.jooq.public.tables.references.RECIPES
import rosemary_and_thyme.models.exceptions.BadRecipeFormat
import rosemary_and_thyme.models.responses.RecipeContentDTO
import rosemary_and_thyme.models.requests.PostRecipeRequest
import rosemary_and_thyme.models.responses.ImageUploadResponse
import rosemary_and_thyme.models.responses.PostRecipeResponse
import rosemary_and_thyme.models.user.UserPrincipal
import rosemary_and_thyme.services.RecipeService
import rosemary_and_thyme.services.Service
import rosemary_and_thyme.services.s3_service.PresignedOperation
import rosemary_and_thyme.services.s3_service.getPresignedUrl
import rosemary_and_thyme.services.s3_service.recipeImageKey
import java.time.OffsetDateTime
import java.util.UUID

/**
 * Validates and stores a new draft recipe, then prepares direct R2 uploads for its images.
 * Ownership is derived from the authenticated principal, and the database generates the recipe ID.
 *
 * @param request recipe data supplied by the client
 * @param userPrincipal authenticated owner of the recipe
 * @return generated recipe identifier and a presigned PUT URL for every declared image
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
    val recipeId = checkNotNull(
        Service.dsl.insertInto(RECIPES)
            .set(RECIPES.TITLE, request.title)
            .set(RECIPES.DESCRIPTION, request.description)
            .set(RECIPES.SIDE_NOTES, request.sideNote)
            .set(RECIPES.DIFFICULTY, request.difficulty)
            .set(RECIPES.PORTIONS, request.portions)
            .set(RECIPES.CREATED_DATE, OffsetDateTime.now())
            .set(RECIPES.USER_ID, userPrincipal.id)
            .set(RECIPES.USER_NAME, userPrincipal.username)
            .set(RECIPES.CONTENT, contentJson)
            .returning(RECIPES.ID)
            .fetchSingle()
            .getValue(RECIPES.ID)
    ) {
        "The database did not return an id for the inserted recipe"
    }
    val presignedUrls:List<ImageUploadResponse> = request.imageIds.map { id ->
        val objectKey = Service.s3Service.recipeImageKey(userId=userPrincipal.id.toString(),recipeId=recipeId.toString(),imageId=id)
        ImageUploadResponse(
        id,    
        Service.s3Service.getPresignedUrl(PresignedOperation.PUT, objectKey)
        )
    }
    
    return PostRecipeResponse(recipeId = recipeId.toString(), uploads = presignedUrls)



}


