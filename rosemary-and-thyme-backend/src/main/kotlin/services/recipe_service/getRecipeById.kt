package rosemary_and_thyme.services.recipe_service

import kotlinx.serialization.json.Json
import rosemary_and_thyme.jooq.public.tables.references.RECIPES
import rosemary_and_thyme.models.exceptions.ResourceNotFoundException
import rosemary_and_thyme.models.responses.RecipeContentDTO
import rosemary_and_thyme.models.responses.RecipeDTO
import rosemary_and_thyme.models.user.UserPrincipal
import rosemary_and_thyme.services.RecipeService
import rosemary_and_thyme.services.Service
import java.util.UUID


/**
 * Retrieves a recipe visible to the viewer and maps its JSON content column to [RecipeDTO].
 *
 * @param id identifier of the requested recipe
 * @param userPrincipal authenticated viewer, or `null` for a guest
 * @return the matching recipe
 * @throws ResourceNotFoundException if the recipe is missing or private and owned by someone else
 */
fun RecipeService.getRecipeById(id: UUID, userPrincipal: UserPrincipal?): RecipeDTO =
    Service.dsl.selectFrom(RECIPES)
    .where(RECIPES.ID.eq(id))
    .and(
        if (userPrincipal == null) {
            RECIPES.IS_PRIVATE.eq(false)
        } else {
            RECIPES.IS_PRIVATE.eq(false).or(RECIPES.USER_ID.eq(userPrincipal.id))
        }
    )
    .fetchOne()?.let{record->
        val content=Json.decodeFromString<RecipeContentDTO>(checkNotNull(record.get(RECIPES.CONTENT)).data())

        RecipeDTO(
                id=record.get(RECIPES.ID).toString(),
                userId=record.get(RECIPES.USER_ID).toString(),
                userName=checkNotNull(record.get(RECIPES.USER_NAME)),
                title=checkNotNull(record.get(RECIPES.TITLE)),
                description=checkNotNull(record.get(RECIPES.DESCRIPTION)),
                likes=getRecipeLikeCount(id),
                timesMade = checkNotNull(record.get(RECIPES.TIMES_MADE)),
                portions=checkNotNull(record.get(RECIPES.PORTIONS)),
                difficulty = checkNotNull(record.get(RECIPES.DIFFICULTY)),
                sideNote=checkNotNull(record.get(RECIPES.SIDE_NOTES)),
                createdDate=record.get(RECIPES.CREATED_DATE).toString(),
                isPrivate=checkNotNull(record.get(RECIPES.IS_PRIVATE)),

                    
                imageIds=content.imageIds,
                ingredients=content.ingredients,
                instructions=content.instructions,
                ingredientsOrder=content.ingredientsOrder,
                heroImagesOrder=content.heroImagesOrder,
                instructionsOrder=content.instructionsOrder
            )
    } ?: throw ResourceNotFoundException("Recipe with that id does not exist")
