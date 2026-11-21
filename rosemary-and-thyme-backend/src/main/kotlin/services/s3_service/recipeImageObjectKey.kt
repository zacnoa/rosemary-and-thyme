package rosemary_and_thyme.services.s3_service

import rosemary_and_thyme.services.S3Service

/**
 * Builds the R2 prefix shared by all images belonging to a recipe.
 *
 * @param userId owner of the recipe
 * @param recipeId recipe containing the images
 * @return prefix namespaced by user and recipe
 */
fun S3Service.recipeImageObjectKeyPrefix(
    userId: String,
    recipeId: String
): String =
    "users/$userId/recipes/$recipeId/images/"

/**
 * Builds the deterministic R2 object key for an image belonging to a recipe.
 * The supplied identifiers are assumed to have already been validated and authorized.
 *
 * @param userId owner of the recipe
 * @param recipeId recipe containing the image
 * @param imageId client-generated image identifier
 * @return object key namespaced by user, recipe, and image
 */
fun S3Service.recipeImageObjectKey(
    userId: String,
    recipeId: String,
    imageId: String
): String =
    "${recipeImageObjectKeyPrefix(userId, recipeId)}$imageId"
