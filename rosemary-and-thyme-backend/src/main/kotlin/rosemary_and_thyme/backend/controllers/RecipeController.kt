package rosemary_and_thyme.backend.controllers

import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
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


    @PutMapping("/{id}")
    fun saveRecipe(@PathVariable id: UUID,@RequestBody recipe: RecipeDTO){

        recipeService.saveRecipe(id,recipe)
    }
}