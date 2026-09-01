package rosemary_and_thyme.backend.models.recipes

import java.time.LocalDate
import java.util.UUID

/**
 * One card's worth of data for the global recipe feed (home page) - a lighter
 * projection of [RecipeDTO] with just what a feed card needs, plus a single resolved
 * preview image, rather than the full recipe (ingredients/instructions/every image).
 * Assembled by [rosemary_and_thyme.backend.services.recipes.RecipeService.searchRecipeFeed]
 * from [rosemary_and_thyme.backend.repositories.recipes.RecipeRepository.selectRecipeFeed]
 * + [rosemary_and_thyme.backend.repositories.recipes.RecipeRepository.selectFirstHeroImages].
 *
 * @property id the recipe's id
 * @property name the recipe's title
 * @property description a short description of the recipe
 * @property likes the recipe's like count
 * @property userName the recipe owner's display name
 * @property createDate the date the recipe was created
 * @property heroImageUrl the recipe's first (lowest-index) hero image url, or `null`
 * if it has no hero image at all - a feed card simply omits the image in that case
 * @property isPrivate mirrors [RecipeDTO.isPrivate] - only ever `true` on a card
 * returned from the dashboard's own-recipes feed (the global feed always excludes
 * private recipes, see [rosemary_and_thyme.backend.repositories.recipes.RecipeRepository.searchConditions]),
 * used there to render a "Private" badge next to the toggle
 */
data class RecipeFeedDTO(
    val id: UUID,
    val name: String,
    val description: String,
    val likes: Long,
    val userName: String,
    val createDate: LocalDate,
    val heroImageUrl: String?,
    val isPrivate: Boolean
)
