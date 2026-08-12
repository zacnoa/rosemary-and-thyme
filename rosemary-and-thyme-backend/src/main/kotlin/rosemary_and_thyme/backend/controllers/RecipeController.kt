package rosemary_and_thyme.backend.controllers

import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.util.MultiValueMap
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import rosemary_and_thyme.backend.jooqgenerated.app_auth.tables.records.UsersRecord
import rosemary_and_thyme.backend.models.recipes.RecipeDTO
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
     *
     * @param id the recipe's id
     * @return 200 OK with the full [RecipeDTO], or a 404 problem-detail body (via
     * [GlobalExceptionHandler.handleNotFound]) if no recipe exists with that id
     */
    @GetMapping("/{id}")
    fun getRecipeById(@PathVariable id: UUID): ResponseEntity<RecipeDTO> {
        val recipe = recipeService.findRecipeById(id)
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
     * [rosemary_and_thyme.backend.repositories.recipes.RecipeRepository.selectRecipesWithFilters]).
     * The scoped, "my recipes only" equivalent is [UserController.searchOwnRecipes].
     *
     * @param q optional name filter; when omitted, results are sorted alphabetically
     * instead of by match relevance
     * @param userId optional filter to only this user's recipes
     * @return 200 OK with matching recipes, capped at 100, as (id, name) pairs
     */
    @GetMapping("/search")
    fun searchRecipes(
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) userId: UUID?
    ): ResponseEntity<List<Pair<UUID, String>>> =
        ResponseEntity.ok(recipeService.searchRecipes(q, userId))

}
