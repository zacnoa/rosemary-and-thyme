package rosemaryandthyme.backend.models.recipes

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty

/** Body of `PATCH /recipe/{id}/private` - see [rosemaryandthyme.backend.controllers.RecipeController.setRecipePrivate]. */
data class SetPrivateRequest @JsonCreator constructor(@JsonProperty("isPrivate") val isPrivate: Boolean)
