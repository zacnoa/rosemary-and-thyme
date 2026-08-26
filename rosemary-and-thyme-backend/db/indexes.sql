-- All indexes for the schema in db/schema.sql. Indexes have no bearing on the
-- Kotlin classes jOOQ generates (table/column structure is all that matters
-- for that), so none of them are fed to jOOQ's DDLDatabase codegen - apply
-- this file after schema.sql when setting up a database.

-- FK columns are not auto-indexed by Postgres; SessionService looks sessions up
-- by session_hash today, but cascading deletes from app_auth.users and any future
-- "list/revoke sessions for user" query need this.
CREATE INDEX IF NOT EXISTS sessions_user_id_idx
    ON app_auth.sessions(user_id);

-- EmailVerificationRepository looks up tokens by user_id when invalidating
-- outstanding tokens on resend, and this FK column isn't auto-indexed.
CREATE INDEX IF NOT EXISTS email_verification_tokens_user_id_idx
    ON app_auth.email_verification_tokens(user_id);

-- OauthIdentityRepository looks identities up by (provider, provider_subject),
-- already covered by oauth_identities_provider_subject_key's own index - this
-- one is purely for the FK column itself, same reasoning as the other two
-- user_id indexes above/below.
CREATE INDEX IF NOT EXISTS oauth_identities_user_id_idx
    ON app_auth.oauth_identities(user_id);

-- no extra index needed for hero_images.image_id -> images.id: image_id is
-- already the primary key of hero_images

-- recipe_id lookups (fetchHeroImages, fetchHeroImageIds) are covered by the
-- leftmost column of hero_images_recipe_id_index_key

-- recipe_id lookups (fetchIngredients, saveIngredients) are covered by the
-- leftmost column of ingredients_recipe_id_index_key

-- no extra index needed for instruction_images.image_id -> images.id: image_id
-- is already the primary key of instruction_images

-- instruction_id lookups (fetchInstructionImages, saveInstructionImages) are
-- covered by the leftmost column of instruction_images_instruction_id_index_key

-- recipe_id lookups (fetchInstructions, saveInstructions) are covered by the
-- leftmost column of instructions_recipe_id_index_key

-- selectAllRecipes() and selectRecipesWithFilters() filter by user_id;
-- this column had no index at all before.
CREATE INDEX IF NOT EXISTS recipes_user_id_idx
    ON public.recipes(user_id);

-- selectRecipesWithFilters() runs `RECIPES.NAME % :term` and orders by
-- similarity(name, :term) - without this GIN trigram index every search
-- is a full sequential scan over recipes.
CREATE INDEX IF NOT EXISTS recipes_name_trgm_idx
    ON public.recipes USING gin (name gin_trgm_ops);
