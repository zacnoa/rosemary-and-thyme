package rosemary_and_thyme.backend.models.recipes

import java.util.UUID

/**
 * A single image attached to a recipe, either as a hero image or as an instruction
 * step image (the two are stored in different join tables but share this shape on
 * the wire - see [rosemary_and_thyme.backend.repositories.recipes.RecipeRepository]).
 *
 * @property id the image's id. For a new image the client has not yet uploaded, this
 * is a client-generated temporary id used to correlate the DTO with the multipart file
 * in the same request - see [rosemary_and_thyme.backend.controllers.RecipeController.saveRecipe].
 * @property url the Cloudinary URL, or `null` for an image that has not been uploaded yet
 */
data class RecipeImageDTO(
    val id: UUID,
    val url: String?
)
