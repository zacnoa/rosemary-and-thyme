package rosemary_and_thyme.backend.controllers

import org.springframework.http.MediaType
import org.springframework.util.MultiValueMap
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import rosemary_and_thyme.backend.models.ApiResponse
import rosemary_and_thyme.backend.repositories.recipes.RecipeDTO
import rosemary_and_thyme.backend.services.RecipeService
import java.util.UUID


@RestController
@RequestMapping("/recipe")
class RecipeController(private val recipeService: RecipeService) {


    @GetMapping("/{id}")
    fun getRecipeById(@PathVariable id: UUID): ApiResponse<RecipeDTO> =

        recipeService.findRecipeById(id)


    @PutMapping("/{id}", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun saveRecipe(
        @PathVariable id: UUID,
        @RequestPart("recipe") dto: RecipeDTO,
        @RequestParam allParts: MultiValueMap<String, MultipartFile>
    ): ApiResponse<RecipeDTO> {
        val images = allParts
            .filter { (key, _) -> key != "recipe" }
            .mapKeys { UUID.fromString(it.key) }
            .mapValues { it.value.first() }
        return recipeService.saveRecipe(dto, images)
    }
}