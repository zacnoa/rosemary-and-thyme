package rosemary_and_thyme.backend.models.recipes

/**
 * One page of a cursor-paginated recipe feed - see
 * [rosemary_and_thyme.backend.repositories.recipes.RecipeRepository.selectRecipeFeed]/
 * [rosemary_and_thyme.backend.repositories.recipes.RecipeRepository.selectLikedRecipeFeed]
 * for how [nextCursor] is built and
 * [rosemary_and_thyme.backend.repositories.recipes.RecipeCursor] for its actual
 * encoding, and the frontend's `<VirtualFeed/>` (components/common/VirtualFeed.tsx)
 * for how it's consumed - it only ever echoes this value straight back as the next
 * request's `?cursor=`, never inspects it.
 *
 * @property items this page's recipes, in the feed's sort order
 * @property nextCursor opaque token to pass back as `?cursor=` to fetch the next
 * page, or `null` when [items] already reached the end of the feed
 */
data class RecipeFeedPage(
    val items: List<RecipeFeedDTO>,
    val nextCursor: String?
)
