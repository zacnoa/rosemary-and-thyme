package rosemary_and_thyme.backend.models.recipes

/** One page of a cursor-paginated recipe feed - see [rosemary_and_thyme.backend.repositories.recipes.RecipeRepository.selectRecipeFeed]/… */
data class RecipeFeedPage(
    val items: List<RecipeFeedDTO>,
    val nextCursor: String?
)
