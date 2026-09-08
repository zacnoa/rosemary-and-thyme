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

/** Exposes endpoints for the currently authenticated user. */
@RestController
@RequestMapping("/user")
class UserController(
    private val recipeService: RecipeService,
    private val authService: AuthService,
    private val accountDeletionService: AccountDeletionService,
    private val cookies: SessionCookieFactory
) {

    /** Returns the current user's public account details.
     * @param user the authenticated user.
     * @return `200 OK` with the account details.
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

    /** Changes the current user's password.
     * @param request the current and replacement passwords.
     * @param user the authenticated user.
     * @return `200 OK` with a replacement session cookie.
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

    /** Schedules the current account for deletion.
     * @param user the authenticated user.
     * @return `200 OK` after scheduling deletion.
     */
    @PostMapping("/delete")
    fun requestAccountDeletion(@AuthenticationPrincipal user: UsersRecord): ResponseEntity<Unit> {
        accountDeletionService.requestDeletion(user.id.dbNotNull("id"))
        return ResponseEntity.ok().build()
    }

    /** Cancels the current account's pending deletion.
     * @param user the authenticated user.
     * @return `200 OK` after cancelling deletion.
     */
    @DeleteMapping("/delete")
    fun cancelAccountDeletion(@AuthenticationPrincipal user: UsersRecord): ResponseEntity<Unit> {
        accountDeletionService.cancelDeletion(user.id.dbNotNull("id"))
        return ResponseEntity.ok().build()
    }

    /** Returns a paginated feed of the current user's recipes.
     * @param q the optional fuzzy recipe-name query.
     * @param cursor the optional previous-page cursor.
     * @param user the authenticated user.
     * @return `200 OK` with one recipe-feed page.
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

    /** Returns a paginated feed of recipes liked by the current user.
     * @param cursor the optional previous-page cursor.
     * @param user the authenticated user.
     * @return `200 OK` with one recipe-feed page.
     */
    @GetMapping("/recipes/liked")
    fun searchLikedRecipes(
        @RequestParam(required = false) cursor: String?,
        @AuthenticationPrincipal user: UsersRecord
    ): ResponseEntity<RecipeFeedPage> =
        ResponseEntity.ok(recipeService.likedRecipeFeed(user.id.dbNotNull("id"), cursor))

}
