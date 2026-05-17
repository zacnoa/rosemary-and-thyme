package rosemary_and_thyme.backend.repositories.recipes

import rosemary_and_thyme.backend.jooqgenerated.public.tables.HeroImages.Companion.HERO_IMAGES
import rosemary_and_thyme.backend.jooqgenerated.public.tables.Ingredients.Companion.INGREDIENTS
import rosemary_and_thyme.backend.jooqgenerated.public.tables.InstructionImages.Companion.INSTRUCTION_IMAGES
import rosemary_and_thyme.backend.jooqgenerated.public.tables.Instructions.Companion.INSTRUCTIONS
import rosemary_and_thyme.backend.jooqgenerated.public.tables.Recipes.Companion.RECIPES
import rosemary_and_thyme.backend.jooqgenerated.public.tables.records.HeroImagesRecord
import rosemary_and_thyme.backend.jooqgenerated.public.tables.records.IngredientsRecord
import rosemary_and_thyme.backend.jooqgenerated.public.tables.records.InstructionImagesRecord
import rosemary_and_thyme.backend.jooqgenerated.public.tables.records.InstructionsRecord
import rosemary_and_thyme.backend.jooqgenerated.public.tables.records.RecipesRecord
import java.util.UUID

 fun RecipeRepository.fetchRecipeBase(recipeId: UUID): RecipesRecord? =
    dsl.selectFrom(RECIPES)
        .where(RECIPES.ID.eq(recipeId))
        .fetchOneInto(RecipesRecord::class.java)



 fun RecipeRepository.fetchIngredients(recipeId: UUID): List<IngredientsRecord> =
    dsl.selectFrom(INGREDIENTS)
        .where(INGREDIENTS.RECIPE_ID.eq(recipeId))
        .orderBy(INGREDIENTS.INDEX)
        .fetchInto(IngredientsRecord::class.java)

 fun RecipeRepository.fetchInstructions(recipeId: UUID): List<InstructionsRecord> =
    dsl.selectFrom(INSTRUCTIONS)
        .where(INSTRUCTIONS.RECIPE_ID.eq(recipeId))
        .orderBy(INSTRUCTIONS.INDEX)
        .fetchInto(InstructionsRecord::class.java)

 fun RecipeRepository.fetchHeroImages(recipeId: UUID): List<HeroImagesRecord> =
    dsl.selectFrom(HERO_IMAGES)
        .where(HERO_IMAGES.RECIPE_ID.eq(recipeId))
        .orderBy(HERO_IMAGES.INDEX)
        .fetchInto(HeroImagesRecord::class.java)

 fun RecipeRepository.fetchInstructionImages(instructionIds: List<UUID>): Map<UUID, List<InstructionImagesRecord>> =
    dsl.selectFrom(INSTRUCTION_IMAGES)
        .where(INSTRUCTION_IMAGES.INSTRUCTION_ID.`in`(instructionIds))
        .orderBy(INSTRUCTION_IMAGES.INDEX)
        .fetchInto(InstructionImagesRecord::class.java)
        .groupBy { it.instructionId }
