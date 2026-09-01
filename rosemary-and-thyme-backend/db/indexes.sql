-- All indexes for the schema in db/schema.sql. Indexes have no bearing on the
-- Kotlin classes jOOQ generates (table/column structure is all that matters
-- for that), so none of them are fed to jOOQ's DDLDatabase codegen - apply
-- this file after schema.sql when setting up a database.

-- FK columns are not auto-indexed by Postgres; SessionService looks sessions up
-- by session_hash today, but cascading deletes from app_auth.users and any future
-- "list/revoke sessions for user" query need this.
CREATE INDEX IF NOT EXISTS sessions_user_id_idx
    ON app_auth.sessions(user_id);

-- SessionCleanupService.purgeInvalidSessions runs `revoked = true OR expires_at <
-- now()` daily; this covers both sides of that OR (a plain btree on the leading
-- boolean column is enough to short-circuit the `revoked = true` rows, and its
-- second column serves the `expires_at < now()` rows without a second index).
CREATE INDEX IF NOT EXISTS sessions_revoked_expires_at_idx
    ON app_auth.sessions(revoked, expires_at);

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

-- PasswordResetRepository looks up tokens by user_id when invalidating
-- outstanding tokens on a fresh request, same reasoning as
-- email_verification_tokens_user_id_idx above.
CREATE INDEX IF NOT EXISTS password_reset_tokens_user_id_idx
    ON app_auth.password_reset_tokens(user_id);

-- AccountDeletionService.purgeExpiredAccounts filters on this column daily;
-- partial index since almost every row has it null (not scheduled for deletion).
CREATE INDEX IF NOT EXISTS users_deletion_requested_at_idx
    ON app_auth.users(deletion_requested_at) WHERE deletion_requested_at IS NOT NULL;

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

-- selectRecipeFeed()'s cursor-paginated `orderByLikes = true` path (the home
-- page's global feed, most-liked first) both orders by and keyset-paginates on
-- `(likes, id)` - see RecipeCursor's KDoc for why `id` is always the tiebreak.
-- DESC on both since that's the order the query itself runs in.
CREATE INDEX IF NOT EXISTS recipes_likes_id_idx
    ON public.recipes(likes DESC, id DESC);

-- Same reasoning as recipes_likes_id_idx above, for selectRecipeFeed()'s other
-- (non-`q`, non-`orderByLikes`) path - the dashboard's default alphabetical
-- order. recipes_name_trgm_idx above doesn't cover this: that one's a GIN
-- trigram index for `%` similarity search, not a btree an ORDER BY name/keyset
-- comparison can seek on.
CREATE INDEX IF NOT EXISTS recipes_name_id_idx
    ON public.recipes(name ASC, id ASC);

-- selectLikedRecipeFeed() filters recipe_likes by user_id and both orders by and
-- keyset-paginates on `(created_at, recipe_id)` - recipe_likes_pkey (recipe_id,
-- user_id) doesn't help here since user_id isn't its leading column and it
-- carries no created_at ordering at all.
CREATE INDEX IF NOT EXISTS recipe_likes_user_created_idx
    ON public.recipe_likes(user_id, created_at DESC, recipe_id DESC);
