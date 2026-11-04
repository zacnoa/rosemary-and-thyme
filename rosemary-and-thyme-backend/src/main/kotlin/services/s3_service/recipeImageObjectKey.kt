package rosemary_and_thyme.services.s3_service

import rosemary_and_thyme.services.S3Service
import java.util.UUID

fun S3Service.recipeImageKey(
    userId: UUID,
    recipeId: UUID,
    imageId: UUID
): String =
    "users/$userId/recipes/$recipeId/images/$imageId"