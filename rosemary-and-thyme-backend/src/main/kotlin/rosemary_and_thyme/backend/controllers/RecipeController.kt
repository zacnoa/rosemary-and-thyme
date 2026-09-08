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

/** Exposes recipe retrieval, editing, search, visibility, and like endpoints. */

@RestController
@RequestMapping("/recipe")
class RecipeController(private val recipeService: RecipeService) {

    /** Returns a recipe when the caller may view it.
     * @param id the recipe id.
     * @param user the optional authenticated caller.
     * @return `200 OK` with the recipe.
     */
    @GetMapping("/{id}")
    fun getRecipeById(
        @PathVariable id: UUID,
        @AuthenticationPrincipal user: UsersRecord?
    ): ResponseEntity<RecipeDTO> {
        val recipe = recipeService.findRecipeById(id, user?.id)
        return ResponseEntity.ok(recipe)
    }

    /** Creates a recipe from multipart recipe data and image files.
     * @param dto the submitted recipe state.
     * @param allParts all multipart request parts.
     * @param user the authenticated owner.
     * @return `200 OK` with the persisted recipe.
     */
    @PostMapping(consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun createRecipe(
        @RequestPart("recipe") dto: RecipeWriteDTO,
        @RequestParam allParts: MultiValueMap<String, MultipartFile>,
        @AuthenticationPrincipal user: UsersRecord
    ): ResponseEntity<RecipeDTO> =
        ResponseEntity.ok(recipeService.createRecipe(dto, imageParts(allParts), user))

    /** Updates an existing recipe from multipart recipe data and image files.
     * @param id the recipe id.
     * @param dto the submitted recipe state.
     * @param allParts all multipart request parts.
     * @param user the authenticated owner.
     * @return `200 OK` with the persisted recipe.
     */
    @PutMapping("/{id}", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun saveRecipe(
        @PathVariable id: UUID,
        @RequestPart("recipe") dto: RecipeWriteDTO,
        @RequestParam allParts: MultiValueMap<String, MultipartFile>,
        @AuthenticationPrincipal user: UsersRecord
    ): ResponseEntity<RecipeDTO> {
        val recipe = recipeService.saveRecipe(id, dto, imageParts(allParts), user)
        return ResponseEntity.ok(recipe)
    }

    /** Extracts image files keyed by their temporary image ids.
     * @param allParts every multipart request part.
     * @return image parts keyed by UUID.
     * @throws IllegalArgumentException if an image key contains multiple files.
     */
    private fun imageParts(allParts: MultiValueMap<String, MultipartFile>): Map<UUID, MultipartFile> {
        require(allParts.filterKeys { it != "recipe" }.values.all { it.size == 1 }) {
            "Each image part must have exactly one file"
        }
        return allParts
            .filterKeys { it != "recipe" }
            .mapKeys { UUID.fromString(it.key) }
            .mapValues { it.value.single() }
    }

    /** Searches public recipes using the global feed order.
     * @param q the optional fuzzy name query.
     * @param userId the optional owner filter.
     * @param cursor the optional previous-page cursor.
     * @return `200 OK` with one recipe-feed page.
     */
    @GetMapping("/search")
    fun searchRecipes(
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) userId: UUID?,
        @RequestParam(required = false) cursor: String?
    ): ResponseEntity<RecipeFeedPage> =
        ResponseEntity.ok(recipeService.searchRecipeFeed(q, userId, orderByLikes = true, excludePrivate = true, cursor = cursor))

    /** Adds the caller's like to a recipe.
     * @param id the recipe id.
     * @param user the authenticated caller.
     * @return `200 OK` with the updated like state.
     */
    @PostMapping("/{id}/like")
    fun likeRecipe(
        @PathVariable id: UUID,
        @AuthenticationPrincipal user: UsersRecord
    ): ResponseEntity<LikeStatusDTO> =
        ResponseEntity.ok(recipeService.likeRecipe(id, user))

    /** Removes the caller's like from a recipe.
     * @param id the recipe id.
     * @param user the authenticated caller.
     * @return `200 OK` with the updated like state.
     */
    @DeleteMapping("/{id}/like")
    fun unlikeRecipe(
        @PathVariable id: UUID,
        @AuthenticationPrincipal user: UsersRecord
    ): ResponseEntity<LikeStatusDTO> =
        ResponseEntity.ok(recipeService.unlikeRecipe(id, user))

    /** Deletes a recipe owned by the caller.
     * @param id the recipe id.
     * @param user the authenticated caller.
     * @return `204 No Content` after deletion.
     */
    @DeleteMapping("/{id}")
    fun deleteRecipe(
        @PathVariable id: UUID,
        @AuthenticationPrincipal user: UsersRecord
    ): ResponseEntity<Void> {
        recipeService.deleteRecipe(id, user)
        return ResponseEntity.noContent().build()
    }

    /** Changes a recipe's private visibility flag.
     * @param id the recipe id.
     * @param request the requested visibility.
     * @param user the authenticated caller.
     * @return `200 OK` after updating visibility.
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
