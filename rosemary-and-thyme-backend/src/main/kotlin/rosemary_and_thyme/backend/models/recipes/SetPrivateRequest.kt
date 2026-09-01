package rosemary_and_thyme.backend.models.recipes

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Body of `PATCH /recipe/{id}/private` - see
 * [rosemary_and_thyme.backend.controllers.RecipeController.setRecipePrivate].
 *
 * The explicit `@JsonCreator`/`@JsonProperty` below are load-bearing, not decorative:
 * with a single `Boolean` constructor parameter and nothing else, Jackson can't tell
 * whether this type should deserialize from a bare JSON scalar (`true`) via a
 * "delegating" creator or from an object (`{"isPrivate": true}`) via a "properties"
 * creator, and fails with `no delegate- or property-based Creator` for the latter -
 * confirmed empirically against this exact class (every other single-field request
 * DTO in this codebase - e.g. [rosemary_and_thyme.backend.models.auth.VerifyEmailRequest] -
 * happens to hold a `String`, not a lone `Boolean`, so this ambiguity never came up
 * before). `@JsonProperty` on the parameter pins it to properties-mode explicitly,
 * sidestepping the ambiguity outright rather than relying on jackson-module-kotlin to
 * infer it.
 *
 * @property isPrivate the recipe's new [RecipeDTO.isPrivate] value
 */
data class SetPrivateRequest @JsonCreator constructor(@JsonProperty("isPrivate") val isPrivate: Boolean)
