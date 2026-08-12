package rosemary_and_thyme.backend.services.user

import org.springframework.stereotype.Service
import rosemary_and_thyme.backend.repositories.recipes.RecipeRepository
import java.util.UUID

/**
 * Empty stub - no methods yet. [rosemary_and_thyme.backend.controllers.UserController]
 * currently calls [rosemary_and_thyme.backend.services.recipes.RecipeService] directly
 * instead of going through this class, so [recipeRepository] is unused.
 */
@Service
class UserService(private val recipeRepository: RecipeRepository) {


}
