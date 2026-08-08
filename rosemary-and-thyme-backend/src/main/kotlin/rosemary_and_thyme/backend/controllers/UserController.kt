package rosemary_and_thyme.backend.controllers

import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import rosemary_and_thyme.backend.jooqgenerated.app_auth.tables.records.UsersRecord
import rosemary_and_thyme.backend.repositories.dbNotNull
import rosemary_and_thyme.backend.services.recipes.RecipeService
import java.util.UUID

@RestController
@RequestMapping("/user")
class UserController(private val recipeService: RecipeService) {

    @GetMapping("/aboutme")
    fun aboutMe(@AuthenticationPrincipal user: UsersRecord): ResponseEntity<UserDTO> {
        return ResponseEntity.ok().body(UserDTO(user.id.dbNotNull("id"), user.username, user.email.dbNotNull("email")))

    }

    @GetMapping("/recipes/search")
    fun searchOwnRecipes(
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) difficulty: Int?,
        @AuthenticationPrincipal user: UsersRecord
    ): ResponseEntity<List<Pair<UUID, String>>> =
        ResponseEntity.ok(recipeService.searchRecipes(q = q, userId = user.id))

}