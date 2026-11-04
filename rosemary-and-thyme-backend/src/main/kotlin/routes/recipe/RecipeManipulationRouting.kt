package rosemary_and_thyme.routes.recipe

import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import rosemary_and_thyme.models.requests.PostRecipeRequest
import rosemary_and_thyme.models.requests.UpdateRecipeRequest
import rosemary_and_thyme.models.user.UserPrincipal
import rosemary_and_thyme.routes.utils.requireUuidPathParameter
import rosemary_and_thyme.services.Service
import rosemary_and_thyme.services.recipe_service.*

/** Registers routes that create, update, and delete recipe data. */
fun Route.recipeManipulationRoutes() {
    authenticate("auth-session") {

        post {
            val request = call.receive<PostRecipeRequest>()
            val userPrincipal = checkNotNull(call.principal<UserPrincipal>())
            val response = Service.recipeService.postRecipe(request, userPrincipal)

            call.respond(
                status = HttpStatusCode.Created,
                message = response
            )
        }
        post("/{id}/complete") {
            val recipeId = call.requireUuidPathParameter("id")
            val userPrincipal = checkNotNull(call.principal<UserPrincipal>())

            Service.recipeService.completeRecipeDraft(
                recipeId = recipeId,
                userPrincipal = userPrincipal
            )

            call.respond(HttpStatusCode.NoContent)
        }
        // Replaces an existing recipe.
        put("/{id}") {
            val request = call.receive<UpdateRecipeRequest>()

            val id = call.requireUuidPathParameter("id")

            val userPrincipal = checkNotNull(call.principal<UserPrincipal>())

            Service.recipeService.putRecipe(request, userPrincipal, id)

            call.respond(status = HttpStatusCode.OK, message = "Recipe was successfully saved")
        }
        // Creates a like for a recipe.
        post("/{id}/like") {
            val id = call.requireUuidPathParameter("id")
            val userPrincipal = checkNotNull(call.principal<UserPrincipal>())
            Service.recipeService.postLike(id,userPrincipal)
        }
        // Removes the current user's like from a recipe.
        delete("/{id}/like") {
            val id = call.requireUuidPathParameter("id")
            val userPrincipal = checkNotNull(call.principal<UserPrincipal>())
            Service.recipeService.deleteLike(id,userPrincipal)
            
        }

        // Records that a recipe was made.
        post("/{id}/made") {
            val id = call.requireUuidPathParameter("id")

            Service.recipeService.postMade(id)

            call.respond(
                status = HttpStatusCode.OK,
                message = "Recipe made counter was successfully updated"
            )
        }
        // Deletes a recipe.
        delete("/{id}") {
            val id = call.requireUuidPathParameter("id")
            val userPrincipal = checkNotNull(call.principal<UserPrincipal>())

            Service.recipeService.deleteRecipe(id, userPrincipal)

            call.respond(HttpStatusCode.NoContent)
        }
        // Toggles a recipe's private flag.
        patch("/{id}/private") {
            val id = call.requireUuidPathParameter("id")
            val userPrincipal = checkNotNull(call.principal<UserPrincipal>())

            Service.recipeService.toggleRecipePrivate(id, userPrincipal)

            call.respond(
                status = HttpStatusCode.OK,
                message = "Recipe privacy was successfully updated"
            )
        }
    }
}
