package rosemaryandthyme.backend.models.recipes

/** One page of a cursor-paginated recipe feed - see [rosemaryandthyme.backend.repositories.recipes.RecipeRepository.selectRecipeFeed]/… */
data class RecipeFeedPage(
    val items: List<RecipeFeedDTO>,
    val nextCursor: String?
)
