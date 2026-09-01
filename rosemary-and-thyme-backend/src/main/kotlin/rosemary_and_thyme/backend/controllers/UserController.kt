package rosemary_and_thyme.backend.controllers

import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import rosemary_and_thyme.backend.jooqgenerated.app_auth.tables.records.UsersRecord
import rosemary_and_thyme.backend.models.auth.ChangePasswordRequest
import rosemary_and_thyme.backend.models.auth.UserDTO
import rosemary_and_thyme.backend.models.recipes.RecipeFeedDTO
import rosemary_and_thyme.backend.models.recipes.RecipeFeedPage
import rosemary_and_thyme.backend.repositories.dbNotNull
import rosemary_and_thyme.backend.services.auth.AccountDeletionService
import rosemary_and_thyme.backend.services.auth.AuthService
import rosemary_and_thyme.backend.services.auth.SessionCookieFactory
import rosemary_and_thyme.backend.services.recipes.RecipeService
import java.util.UUID

/**
 * Endpoints scoped to the currently authenticated user. [UsersRecord] is injected as
 * `@AuthenticationPrincipal` because [rosemary_and_thyme.backend.filters.SessionAuthFilter]
 * puts it directly on the Spring Security context after validating the session cookie.
 */
@RestController
@RequestMapping("/user")
class UserController(
    private val recipeService: RecipeService,
    private val authService: AuthService,
    private val accountDeletionService: AccountDeletionService,
    private val cookies: SessionCookieFactory
) {

    /**
     * @param user the authenticated caller, resolved from the session cookie
     * @return 200 OK with the caller's public profile
     */
    @GetMapping("/aboutme")
    fun aboutMe(@AuthenticationPrincipal user: UsersRecord): ResponseEntity<UserDTO> {
        return ResponseEntity.ok().body(
            UserDTO(
                id = user.id.dbNotNull("id"),
                username = user.username,
                email = user.email.dbNotNull("email"),
                deletionRequestedAt = user.deletionRequestedAt,
                hasPassword = user.password != null
            )
        )
    }

    /**
     * Changes the caller's password (current + new, no email round trip - see
     * [AuthService.changePassword]). Every other session for the account is revoked
     * and this request gets a fresh session cookie, so the caller stays logged in here
     * while any other device is signed out.
     *
     * @param request the current password (checked) and the new one to set
     * @param user the authenticated caller
     * @return 200 OK with a fresh session cookie attached
     */
    @PutMapping("/password")
    fun changePassword(
        @RequestBody request: ChangePasswordRequest,
        @AuthenticationPrincipal user: UsersRecord
    ): ResponseEntity<Unit> {
        val sessionToken = authService.changePassword(user, request.currentPassword, request.newPassword)

        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, cookies.session(sessionToken).toString())
            .build()
    }

    /**
     * Schedules the caller's account for deletion 30 days from now (see
     * [AccountDeletionService]). The account stays fully usable for that whole grace
     * period - this only sets a flag the frontend uses to show a "cancel?" banner
     * (`UserDTO.deletionRequestedAt`) and a daily job checks against later.
     *
     * @param user the authenticated caller
     * @return 200 OK with an empty body
     */
    @PostMapping("/delete")
    fun requestAccountDeletion(@AuthenticationPrincipal user: UsersRecord): ResponseEntity<Unit> {
        accountDeletionService.requestDeletion(user.id.dbNotNull("id"))
        return ResponseEntity.ok().build()
    }

    /**
     * Cancels a pending account deletion, if the caller requested one within the last
     * 30 days. No-ops (still 200) if none was pending.
     *
     * @param user the authenticated caller
     * @return 200 OK with an empty body
     */
    @DeleteMapping("/delete")
    fun cancelAccountDeletion(@AuthenticationPrincipal user: UsersRecord): ResponseEntity<Unit> {
        accountDeletionService.cancelDeletion(user.id.dbNotNull("id"))
        return ResponseEntity.ok().build()
    }

    /**
     * Searches recipes owned by the authenticated caller only - the counterpart of
     * [RecipeController.searchRecipes], which searches across all users. Unlike that
     * public endpoint, `excludePrivate` stays `false` here - a caller searching their
     * own recipes must still find the private ones.
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
        ResponseEntity.ok(recipeService.searchRecipes(q = q, userId = user.id, excludePrivate = false))

    /**
     * The dashboard's recipe list - the authenticated caller's own recipes, as full
     * feed cards (see [RecipeFeedDTO]), reusing the exact same
     * [rosemary_and_thyme.backend.services.recipes.RecipeService.searchRecipeFeed] that
     * backs [RecipeController.searchRecipes]'s global feed - see that endpoint's KDoc
     * for why it already accepted a [UUID] `userId` filter in the first place.
     * Alphabetical/relevance order (not by likes) - the dashboard is "manage my own
     * recipes", not a popularity browse.
     *
     * Cursor-paginated (see [RecipeFeedPage]).
     *
     * @param q optional fuzzy name filter
     * @param cursor `null` for the first page
     * @param user the authenticated caller, whose recipes are searched
     * @return 200 OK with a page of matching recipes as feed cards
     */
    @GetMapping("/recipes/feed")
    fun searchUserFeed(
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) cursor: String?,
        @AuthenticationPrincipal user: UsersRecord
    ): ResponseEntity<RecipeFeedPage> =
        ResponseEntity.ok(
            recipeService.searchRecipeFeed(q = q, userId = user.id, orderByLikes = false, excludePrivate = false, cursor = cursor)
        )

    /**
     * The dashboard's "Liked Recipes" section - every recipe the authenticated caller
     * has liked, most recently liked first, as full feed cards (see [RecipeFeedDTO]).
     * Counterpart of [searchUserFeed] (the caller's *own* recipes) - together they back
     * the dashboard's two scrollable sections. See
     * [rosemary_and_thyme.backend.repositories.recipes.RecipeRepository.selectLikedRecipeFeed]
     * for why this has no name filter and isn't affected by a recipe becoming private
     * after the caller liked it. Cursor-paginated same as [searchUserFeed].
     *
     * @param cursor `null` for the first page
     * @param user the authenticated caller, whose likes are listed
     * @return 200 OK with a page of liked recipes as feed cards, most recently liked first
     */
    @GetMapping("/recipes/liked")
    fun searchLikedRecipes(
        @RequestParam(required = false) cursor: String?,
        @AuthenticationPrincipal user: UsersRecord
    ): ResponseEntity<RecipeFeedPage> =
        ResponseEntity.ok(recipeService.likedRecipeFeed(user.id.dbNotNull("id"), cursor))

}
