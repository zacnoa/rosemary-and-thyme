package rosemary_and_thyme.backend.models.recipes

import java.time.LocalDate
import java.util.UUID

/**
 * Full representation of a recipe as sent to/from the frontend - mirrors the recipe
 * editor's model on the frontend side. Assembled from several tables by
 * [rosemary_and_thyme.backend.services.recipes.RecipeUtils.buildRecipeDTO] and persisted
 * across several tables by [rosemary_and_thyme.backend.repositories.recipes.RecipeRepository].
 *
 * jOOQ generates nullable Kotlin types for any column with a database-side DEFAULT
 * (see [rosemary_and_thyme.backend.repositories.dbNotNull]), which is why some of these
 * properties are non-null here despite coming from a nullable jOOQ record field: by the
 * time a row has actually been fetched, the DB schema guarantees it is set.
 *
 * @property id the recipe's id
 * @property userId the id of the recipe's owner
 * @property userName the owner's display name, denormalized onto the recipe row so recipe
 * listings don't need to join against `app_auth.users`
 * @property createDate the date the recipe was created
 * @property name the recipe's title
 * @property description a short description of the recipe
 * @property likes the recipe's like count
 * @property liked whether the *requesting* caller has liked this recipe - `false`
 * for an anonymous caller or one who simply hasn't liked it (see
 * [rosemary_and_thyme.backend.services.recipes.RecipeService.findRecipeById]); never
 * true for the recipe's own owner, since a recipe's owner can't like it (see
 * [rosemary_and_thyme.backend.services.recipes.RecipeService.likeRecipe])
 * @property portions how many portions the recipe makes
 * @property cookTime a free-text cook time (e.g. "45 min")
 * @property difficulty difficulty rating, constrained to 1..5 by a DB check constraint
 * @property sideNotes optional free-text notes from the author
 * @property images every image attached to the recipe (hero + instruction images), keyed by id
 * @property ingredients the recipe's ingredients, keyed by id
 * @property instructions the recipe's instruction steps, keyed by id
 * @property ingredientsOrder ids of [ingredients], in the order they should be displayed
 * @property instructionsOrder ids of [instructions], in the order they should be displayed
 * @property heroImagesOrder ids of the hero images in [images], in the order they should be displayed
 * @property isPrivate excludes this recipe from the global feed/search and blocks
 * it from anyone but its owner (see [rosemary_and_thyme.backend.services.recipes.RecipeService.findRecipeById]/
 * [rosemary_and_thyme.backend.repositories.recipes.RecipeRepository.searchConditions])
 * when `true`. Toggled from the dashboard via `PATCH /recipe/{id}/private`
 * ([rosemary_and_thyme.backend.controllers.RecipeController.setRecipePrivate]),
 * never from the recipe editor itself - a full [saveRecipe][rosemary_and_thyme.backend.services.recipes.RecipeService.saveRecipe]
 * just carries whatever value it was fetched with straight back through.
 */
data class RecipeDTO(
    val id: UUID,
    val userId: UUID,
    val userName: String,
    val createDate: LocalDate,
    val name: String,
    val description: String,
    val likes: Long,
    val liked: Boolean,
    val portions: Int,
    val cookTime: String,
    val difficulty: Int,
    val sideNotes: String?,
    val images: Map<UUID, RecipeImageDTO>,
    val ingredients: Map<UUID, IngredientDTO>,
    val instructions: Map<UUID, InstructionDTO>,
    val ingredientsOrder: List<UUID>,
    val instructionsOrder: List<UUID>,
    val heroImagesOrder: List<UUID>,
    val isPrivate: Boolean = false
)
