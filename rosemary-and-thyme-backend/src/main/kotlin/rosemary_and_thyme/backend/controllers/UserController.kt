package rosemary_and_thyme.backend.controllers

import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import rosemary_and_thyme.backend.jooqgenerated.app_auth.tables.records.UsersRecord
import rosemary_and_thyme.backend.models.auth.UserDTO
import rosemary_and_thyme.backend.models.recipes.RecipeFeedDTO
import rosemary_and_thyme.backend.repositories.dbNotNull
import rosemary_and_thyme.backend.services.recipes.RecipeService
import java.util.UUID

/**
 * Endpoints scoped to the currently authenticated user. [UsersRecord] is injected as
 * `@AuthenticationPrincipal` because [rosemary_and_thyme.backend.filters.SessionAuthFilter]
 * puts it directly on the Spring Security context after validating the session cookie.
 */
@RestController
@RequestMapping("/user")
class UserController(private val recipeService: RecipeService) {

    /**
     * @param user the authenticated caller, resolved from the session cookie
     * @return 200 OK with the caller's public profile
     */
    @GetMapping("/aboutme")
    fun aboutMe(@AuthenticationPrincipal user: UsersRecord): ResponseEntity<UserDTO> {
        return ResponseEntity.ok().body(UserDTO(user.id.dbNotNull("id"), user.username, user.email.dbNotNull("email")))

    }

    /**
     * Searches recipes owned by the authenticated caller only - the counterpart of
     * [RecipeController.searchRecipes], which searches across all users.
     *
     * @param q optional fuzzy name filter (see [rosemary_and_thyme.backend.repositories.recipes.RecipeRepository.selectRecipesWithFilters])
     * @param user the authenticated caller, whose recipes are searched
     * @return 200 OK with matching recipes as (id, name) pairs
     */
    @GetMapping("/recipes/search")
    fun searchOwnRecipes(
        @RequestParam(required = false) q: String?,
        @AuthenticationPrincipal user: UsersRecord
    ): ResponseEntity<List<Pair<UUID, String>>> =
        ResponseEntity.ok(recipeService.searchRecipes(q = q, userId = user.id))

    /**
     * The dashboard's recipe list - the authenticated caller's own recipes, as full
     * feed cards (see [RecipeFeedDTO]), reusing the exact same
     * [rosemary_and_thyme.backend.services.recipes.RecipeService.searchRecipeFeed] that
     * backs [RecipeController.searchRecipes]'s global feed - see that endpoint's KDoc
     * for why it already accepted a [UUID] `userId` filter in the first place.
     * Alphabetical/relevance order (not by likes) - the dashboard is "manage my own
     * recipes", not a popularity browse.
     *
     * @param q optional fuzzy name filter
     * @param user the authenticated caller, whose recipes are searched
     * @return 200 OK with matching recipes as feed cards
     */
    @GetMapping("/recipes/feed")
    fun searchUserFeed(
        @RequestParam(required = false) q: String?,
        @AuthenticationPrincipal user: UsersRecord
    ): ResponseEntity<List<RecipeFeedDTO>> =
        ResponseEntity.ok(recipeService.searchRecipeFeed(q = q, userId = user.id, orderByLikes = false))

}
