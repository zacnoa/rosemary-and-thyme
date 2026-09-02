package rosemary_and_thyme.backend.controllers

import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.util.MultiValueMap
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import rosemary_and_thyme.backend.jooqgenerated.app_auth.tables.records.UsersRecord
import rosemary_and_thyme.backend.models.recipes.LikeStatusDTO
import rosemary_and_thyme.backend.models.recipes.RecipeDTO
import rosemary_and_thyme.backend.models.recipes.RecipeFeedPage
import rosemary_and_thyme.backend.models.recipes.RecipeWriteDTO
import rosemary_and_thyme.backend.models.recipes.SetPrivateRequest
import rosemary_and_thyme.backend.services.recipes.RecipeService
import java.util.UUID

/**
 * Controller layer.
 */

@RestController
@RequestMapping("/recipe")
class RecipeController(private val recipeService: RecipeService) {

    /**
 * Publicly readable - permitted without auth in the associated behavior.
 * @param id the recipe's id.
 * @param user the authenticated caller, or `null` for an anonymous viewer.
 * @return 200 OK with the full [RecipeDTO], or a 404 problem-detail body (via.
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
 * Creates or updates a recipe (upsert - see [rosemary_and_thyme.backend.repositories.recipes.RecipeRepository.upsertRecipeBase]).
 * @param id the recipe's id (path segment; must match [dto]'s own id)
 * @param dto the recipe's full JSON body, under the "recipe" part.
 * @param allParts every multipart part of the request, including "recipe" and each.
 * @param user the authenticated caller, checked as the recipe's owner.
 * @return 200 OK with the saved recipe, re-fetched so the response reflects the.
 */
    @PostMapping(consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun createRecipe(
        @RequestPart("recipe") dto: RecipeWriteDTO,
        @RequestParam allParts: MultiValueMap<String, MultipartFile>,
        @AuthenticationPrincipal user: UsersRecord
    ): ResponseEntity<RecipeDTO> =
        ResponseEntity.ok(recipeService.createRecipe(dto, imageParts(allParts), user))

    @PutMapping("/{id}", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun saveRecipe(
        @PathVariable id: UUID,
        @RequestPart("recipe") dto: RecipeWriteDTO,
        @RequestParam allParts: MultiValueMap<String, MultipartFile>,
        @AuthenticationPrincipal user: UsersRecord
    ): ResponseEntity<RecipeDTO> {
        /**
 * Filters recipe part from allParts.
 */
        val recipe = recipeService.saveRecipe(id, dto, imageParts(allParts), user)
        return ResponseEntity.ok(recipe)
    }

    private fun imageParts(allParts: MultiValueMap<String, MultipartFile>): Map<UUID, MultipartFile> {
        require(allParts.filterKeys { it != "recipe" }.values.all { it.size == 1 }) {
            "Each image part must have exactly one file"
        }
        return allParts
            .filterKeys { it != "recipe" }
            .mapKeys { UUID.fromString(it.key) }
            .mapValues { it.value.single() }
    }

    /**
 * Fuzzy-searches recipes across all users by name (trigram similarity - see.
 * @param q optional name filter; when omitted, every recipe is eligible, still.
 * @param userId optional filter to only this user's recipes.
 * @param cursor `null` for the first page.
 * @return 200 OK with a page of matching recipes as feed cards, most-liked first.
 */
    @GetMapping("/search")
    fun searchRecipes(
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) userId: UUID?,
        @RequestParam(required = false) cursor: String?
    ): ResponseEntity<RecipeFeedPage> =
        ResponseEntity.ok(recipeService.searchRecipeFeed(q, userId, orderByLikes = true, excludePrivate = true, cursor = cursor))

    /**
 * Likes a recipe on the caller's behalf. Not in the `permitAll()` list in the associated behavior.
 * @param id the recipe to like.
 * @param user the authenticated caller.
 * @return 200 OK with the caller's new like state and the recipe's up-to-date count.
 */
    @PostMapping("/{id}/like")
    fun likeRecipe(
        @PathVariable id: UUID,
        @AuthenticationPrincipal user: UsersRecord
    ): ResponseEntity<LikeStatusDTO> =
        ResponseEntity.ok(recipeService.likeRecipe(id, user))

    /**
 * Removes the caller's like from a recipe. Same auth/idempotency notes as.
 * @param id the recipe to unlike.
 * @param user the authenticated caller.
 * @return 200 OK with the caller's new like state and the recipe's up-to-date count.
 */
    @DeleteMapping("/{id}/like")
    fun unlikeRecipe(
        @PathVariable id: UUID,
        @AuthenticationPrincipal user: UsersRecord
    ): ResponseEntity<LikeStatusDTO> =
        ResponseEntity.ok(recipeService.unlikeRecipe(id, user))

    /**
 * Deletes a recipe (and every image it had) on the caller's behalf. Not in the requested value.
 * @param id the recipe to delete.
 * @param user the authenticated caller.
 * @return 204 No Content on success.
 */
    @DeleteMapping("/{id}")
    fun deleteRecipe(
        @PathVariable id: UUID,
        @AuthenticationPrincipal user: UsersRecord
    ): ResponseEntity<Void> {
        recipeService.deleteRecipe(id, user)
        return ResponseEntity.noContent().build()
    }

    /**
 * Toggles a recipe's private/public flag on the caller's behalf - the dashboard's.
 * @param id the recipe to update.
 * @param request the new `isPrivate` value.
 * @param user the authenticated caller, checked as the recipe's owner.
 * @return 200 OK with an empty body.
 */
    @PatchMapping("/{id}/private")
    fun setRecipePrivate(
        @PathVariable id: UUID,
        @RequestBody request: SetPrivateRequest,
        @AuthenticationPrincipal user: UsersRecord
    ): ResponseEntity<Unit> {
        recipeService.setRecipePrivate(id, request.isPrivate, user)
        return ResponseEntity.ok().build()
    }

}
