package rosemary_and_thyme.backend.controllers

import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.util.MultiValueMap
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import rosemary_and_thyme.backend.jooqgenerated.app_auth.tables.records.UsersRecord
import rosemary_and_thyme.backend.models.recipes.LikeStatusDTO
import rosemary_and_thyme.backend.models.recipes.RecipeDTO
import rosemary_and_thyme.backend.models.recipes.RecipeFeedDTO
import rosemary_and_thyme.backend.services.recipes.RecipeService
import java.util.UUID


/**
 * Controller layer
 *
 * SpringBoot uses Thread-per-request architecture.
 * So in this layer each thread shall receive a request
 * and begin processing it by calling the appropriate
 * function
 */

@RestController
@RequestMapping("/recipe")
class RecipeController(private val recipeService: RecipeService) {

    /**
     * Publicly readable - permitted without auth in
     * [rosemary_and_thyme.backend.configurations.SecurityConfig.securityFilterChain].
     * [user] is still resolved when present (an authenticated caller viewing this
     * without owning it) purely to fill in [RecipeDTO.liked] - it's nullable/optional
     * here specifically because this route stays open to anonymous callers too.
     *
     * @param id the recipe's id
     * @param user the authenticated caller, or `null` for an anonymous viewer
     * @return 200 OK with the full [RecipeDTO], or a 404 problem-detail body (via
     * [GlobalExceptionHandler.handleNotFound]) if no recipe exists with that id
     */
    @GetMapping("/{id}")
    fun getRecipeById(
        @PathVariable id: UUID,
        @AuthenticationPrincipal user: UsersRecord?
    ): ResponseEntity<RecipeDTO> {
        val recipe = recipeService.findRecipeById(id, user?.id)
        return ResponseEntity.ok(recipe)
    }


    /**
     * Creates or updates a recipe (upsert - see [rosemary_and_thyme.backend.repositories.recipes.RecipeRepository.upsertRecipeBase]),
     * together with any new image files attached to it. Requires authentication;
     * ownership of an existing recipe is enforced in [rosemary_and_thyme.backend.services.recipes.RecipeService.saveRecipe].
     *
     * Sent as multipart/form-data rather than plain JSON because the request bundles
     * both the recipe's JSON body and its raw image files in one call.
     *
     * Consumes serves as a safeguard against invalid data types
     *
     * MultiValueMap can have more values mapped to one key.
     * so we use .first() to get only the first valid image file
     *
     * @param id the recipe's id (path segment; must match [dto]'s own id)
     * @param dto the recipe's full JSON body, under the "recipe" part
     * @param allParts every multipart part of the request, including "recipe" and each
     * image file - image parts are keyed by the temporary [UUID] the frontend assigned
     * to that image (see [rosemary_and_thyme.backend.models.recipes.RecipeImageDTO.id])
     * @param user the authenticated caller, checked as the recipe's owner
     * @return 200 OK with the saved recipe, re-fetched so the response reflects the
     * final DB/Cloudinary state (real ids, uploaded urls, etc.)
     */
    @PutMapping("/{id}", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun saveRecipe(
        @PathVariable id: UUID,
        @RequestPart("recipe") dto: RecipeDTO,
        @RequestParam allParts: MultiValueMap<String, MultipartFile>,
        @AuthenticationPrincipal user: UsersRecord
    ): ResponseEntity<RecipeDTO> {
        /**
         * Filters recipe part from allParts
         */
        val images = allParts
            .filter { (key, _) -> key != "recipe" }
            .mapKeys { UUID.fromString(it.key) }
            .mapValues { it.value.first() }

        val recipe = recipeService.saveRecipe(dto, images,user)
        return ResponseEntity.ok(recipe)
    }

    /**
     * Fuzzy-searches recipes across all users by name (trigram similarity - see
     * [rosemary_and_thyme.backend.repositories.recipes.RecipeRepository.selectRecipeFeed]),
     * ordered by like count - this is the "browse/discover popular recipes" endpoint
     * behind the home page's global recipe feed, not a precise lookup, so it always
     * favors popularity over name-match relevance once [q] has narrowed the results.
     * Returns a full [RecipeFeedDTO] per result (not just id/name) since each one is
     * rendered as a feed card. Publicly readable - permitAll in
     * [rosemary_and_thyme.backend.configurations.SecurityConfig.securityFilterChain] -
     * same reasoning as `GET /recipe/{id}`: recipes (and finding them) are public.
     *
     * The scoped, "my recipes only" equivalent - relevance/alphabetical order, not
     * popularity, and the lightweight (id, name) shape rather than a full feed card -
     * is [UserController.searchOwnRecipes]. This endpoint already accepts [userId] too,
     * so a future "my recipes, shown the same way everyone else's are" dashboard view
     * can reuse it directly rather than needing a third route.
     *
     * @param q optional name filter; when omitted, every recipe is eligible
     * (still capped at 100, still ordered by likes)
     * @param userId optional filter to only this user's recipes
     * @return 200 OK with matching recipes, capped at 100, as feed cards, most-liked first
     */
    @GetMapping("/search")
    fun searchRecipes(
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) userId: UUID?
    ): ResponseEntity<List<RecipeFeedDTO>> =
        ResponseEntity.ok(recipeService.searchRecipeFeed(q, userId, orderByLikes = true))

    /**
     * Likes a recipe on the caller's behalf. Not in the `permitAll()` list in
     * [rosemary_and_thyme.backend.configurations.SecurityConfig.securityFilterChain],
     * so an unauthenticated request is rejected before this method body ever runs -
     * the frontend is expected to redirect to login itself rather than let that
     * happen (see queries/likeRecipe.ts), this is the defense-in-depth backstop.
     * Idempotent and ownership-checked - see [RecipeService.likeRecipe].
     *
     * @param id the recipe to like
     * @param user the authenticated caller
     * @return 200 OK with the caller's new like state and the recipe's up-to-date count
     */
    @PostMapping("/{id}/like")
    fun likeRecipe(
        @PathVariable id: UUID,
        @AuthenticationPrincipal user: UsersRecord
    ): ResponseEntity<LikeStatusDTO> =
        ResponseEntity.ok(recipeService.likeRecipe(id, user))

    /**
     * Removes the caller's like from a recipe. Same auth/idempotency notes as
     * [likeRecipe] - see [RecipeService.unlikeRecipe].
     *
     * @param id the recipe to unlike
     * @param user the authenticated caller
     * @return 200 OK with the caller's new like state and the recipe's up-to-date count
     */
    @DeleteMapping("/{id}/like")
    fun unlikeRecipe(
        @PathVariable id: UUID,
        @AuthenticationPrincipal user: UsersRecord
    ): ResponseEntity<LikeStatusDTO> =
        ResponseEntity.ok(recipeService.unlikeRecipe(id, user))

}
