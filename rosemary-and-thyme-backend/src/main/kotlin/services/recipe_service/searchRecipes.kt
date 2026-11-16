package rosemary_and_thyme.services.recipe_service

import io.ktor.server.plugins.BadRequestException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jooq.Condition
import org.jooq.impl.DSL
import org.jooq.impl.SQLDataType
import rosemary_and_thyme.jooq.public.tables.references.RECIPES
import rosemary_and_thyme.jooq.public.tables.references.RECIPE_LIKES
import rosemary_and_thyme.models.responses.RecipeFeedItem
import rosemary_and_thyme.models.user.UserPrincipal
import rosemary_and_thyme.services.CryptoService
import rosemary_and_thyme.services.RecipeService
import rosemary_and_thyme.services.Service
import java.util.UUID

/**
 * Page of recipe summaries before the route attaches signed image URLs.
 *
 * @property items results in descending order by like count, then recipe ID
 * @property nextCursor position of the last returned recipe, or `null` when there is no next page
 */
data class RecipeSearchPage(
    val items: Set<RecipeFeedItem>,
    val nextCursor: String?
)

/**
 * Builds the `HAVING` conditions that continue after a cursor in descending like and recipe ID order.
 *
 * @param likes like count at the cursor position
 * @param recipeId recipe ID used to break ties in like count
 * @return conditions for a query grouped by recipe ID that counts matching rows in `recipe_likes`
 */
fun recipeFeedCursorConditions(likes: Int, recipeId: UUID): List<Condition> =
    listOf(
        DSL.count(RECIPE_LIKES.RECIPE_ID).lt(likes).or(
            DSL.count(RECIPE_LIKES.RECIPE_ID).eq(likes)
                .and(RECIPES.ID.lt(recipeId))
        )
    )

/**
 * Searches published recipes by title with PostgreSQL's trigram similarity operator `%`.
 * The query includes public recipes and the viewer's own private recipes, then fetches one extra
 * matching row to determine whether another page exists.
 *
 * @param query trimmed recipe title search term
 * @param limit maximum number of recipes to return, validated by the route
 * @param cursorToken cursor returned for an earlier page, or `null` for the first page
 * @param userPrincipal authenticated viewer, or `null` for a guest
 * @return ordered recipe summaries and the cursor for the next page, if one exists
 * @throws BadRequestException if [cursorToken] cannot be decoded
 */
suspend fun RecipeService.searchRecipes(
    query: String,
    limit: Int,
    cursorToken: String?,
    userPrincipal: UserPrincipal?
): RecipeSearchPage {
    val viewerId = userPrincipal?.id
    val cursor = cursorToken?.let {
        CryptoService.decodeRecipeFeedCursor(it)
            ?: throw BadRequestException("Invalid recipe search cursor")
    }

    val likes = DSL.count(RECIPE_LIKES.RECIPE_ID).`as`("likes")
    val firstImageId = DSL.field(
        "{0} -> 'imageIds' ->> 0",
        SQLDataType.VARCHAR,
        RECIPES.CONTENT
    ).`as`("first_image_id")

    val rows = withContext(Dispatchers.IO) {
        Service.dsl.select(
            RECIPES.ID,
            RECIPES.USER_ID,
            RECIPES.USER_NAME,
            RECIPES.IS_PRIVATE,
            RECIPES.TITLE,
            likes,
            RECIPES.DESCRIPTION,
            RECIPES.DIFFICULTY,
            RECIPES.PORTIONS,
            RECIPES.CREATED_DATE,
            RECIPES.TIMES_MADE,
            firstImageId
        )
            .from(RECIPES)
            .leftJoin(RECIPE_LIKES).on(RECIPE_LIKES.RECIPE_ID.eq(RECIPES.ID))
            .where(
                RECIPES.IS_DRAFT.eq(false),
                if (viewerId == null) {
                    RECIPES.IS_PRIVATE.eq(false)
                } else {
                    RECIPES.IS_PRIVATE.eq(false).or(RECIPES.USER_ID.eq(viewerId))
                },
                DSL.condition("{0} % {1}", RECIPES.TITLE, DSL.`val`(query))
            )
            .groupBy(RECIPES.ID)
            .having(
                cursor?.let { recipeFeedCursorConditions(it.likes, it.recipeId) }.orEmpty()
            )
            .orderBy(likes.desc(), RECIPES.ID.desc())
            .limit(limit + 1)
            .fetch()
    }

    val items = rows.take(limit).mapTo(linkedSetOf<RecipeFeedItem>()) { row ->
        val recipeId = checkNotNull(row.get(RECIPES.ID)).toString()
        val userId = checkNotNull(row.get(RECIPES.USER_ID)).toString()

        RecipeFeedItem(
            recipeId = recipeId,
            userId = userId,
            username = checkNotNull(row.get(RECIPES.USER_NAME)),
            isPublic = row.get(RECIPES.IS_PRIVATE) == false,
            title = checkNotNull(row.get(RECIPES.TITLE)),
            likes = checkNotNull(row.get(likes)),
            description = checkNotNull(row.get(RECIPES.DESCRIPTION)),
            difficulty = checkNotNull(row.get(RECIPES.DIFFICULTY)),
            portions = checkNotNull(row.get(RECIPES.PORTIONS)),
            createdDate = checkNotNull(row.get(RECIPES.CREATED_DATE)).toString(),
            timesMade = checkNotNull(row.get(RECIPES.TIMES_MADE)),
            imageId = row.get(firstImageId),
            imageUrl = null
        )
    }

    val nextCursor = if (rows.size > limit) {
        val last = rows[limit - 1]
        CryptoService.encodeRecipeFeedCursor(
            likes = checkNotNull(last.get(likes)),
            recipeId = checkNotNull(last.get(RECIPES.ID))
        )
    } else {
        null
    }

    return RecipeSearchPage(items, nextCursor)
}
