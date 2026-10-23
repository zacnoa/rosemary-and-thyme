
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE SCHEMA IF NOT EXISTS app_auth; 


CREATE TABLE IF NOT EXISTS app_auth.users(
    --Automatic PRIMARY KEY index used by queries that fetch, update, or delete a user with `WHERE id = :userId`
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    password varchar(255),
    username varchar(60) NOT NULL,
    --Automatic UNIQUE index used by registration and login queries with `WHERE email = :email`
    email varchar(255) UNIQUE,
    email_verified boolean NOT NULL DEFAULT false,
    deletion_requested_at timestamp with time zone 
);
--Supports account cleanup queries with `WHERE deletion_requested_at < :cutoff`; rows without a deletion request are excluded
CREATE INDEX IF NOT EXISTS users_deletion_requested_at_idx
    ON app_auth.users(deletion_requested_at)
    WHERE deletion_requested_at IS NOT NULL;

CREATE TABLE IF NOT EXISTS app_auth.oauth_identities
(
    --Automatic PRIMARY KEY index used by queries that fetch, update, or delete an OAuth identity with `WHERE id = :identityId`
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    provider varchar(20) NOT NULL, 
    provider_subject varchar(255) NOT NULL,
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    --Automatic UNIQUE index used by OAuth login queries with `WHERE provider = :provider AND provider_subject = :subject`
    CONSTRAINT oauth_identities_provider_subject_key UNIQUE (provider, provider_subject),

    user_id uuid NOT NULL REFERENCES app_auth.users(id)
        ON UPDATE NO ACTION
        ON DELETE CASCADE
);
--Supports identity lookup and cascade cleanup queries with `WHERE user_id = :userId`
CREATE INDEX IF NOT EXISTS oauth_identities_user_id_idx
    ON app_auth.oauth_identities(user_id);

CREATE TABLE IF NOT EXISTS app_auth.sessions(
    --Automatic PRIMARY KEY index used by queries that update or delete a session with `WHERE id = :sessionId`
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    --Automatic UNIQUE index used by authentication queries with `WHERE session_hash = :sessionHash`
    session_hash varchar(64) UNIQUE NOT NULL,
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    expires_at timestamp with time zone NOT NULL DEFAULT (now() + INTERVAL '30' DAY),
    last_used_at timestamp with time zone NOT NULL DEFAULT now(),
    revoked boolean NOT NULL DEFAULT false,

    user_id uuid NOT NULL REFERENCES app_auth.users(id)
        ON UPDATE NO ACTION
        ON DELETE CASCADE
);

--Supports session listing and revocation queries with `WHERE user_id = :userId`
CREATE INDEX IF NOT EXISTS sessions_user_id_idx
    ON app_auth.sessions(user_id);
--Supports session cleanup queries with `WHERE revoked = true`; only revoked rows are stored in this partial index
CREATE INDEX IF NOT EXISTS sessions_revoked_true_idx
    ON app_auth.sessions(id)
    WHERE revoked = true;
--Supports session cleanup queries with `WHERE expires_at < now()`
CREATE INDEX IF NOT EXISTS sessions_expires_at_idx
    ON app_auth.sessions(expires_at);

CREATE TABLE IF NOT EXISTS app_auth.email_verification_tokens(
    --Automatic PRIMARY KEY index used by queries that update or delete a verification token with `WHERE id = :tokenId`
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    --Automatic UNIQUE index used by email verification queries with `WHERE token_hash = :tokenHash`
    token_hash varchar(64) UNIQUE NOT NULL,
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    expires_at timestamp with time zone NOT NULL DEFAULT (now() + INTERVAL '24' HOUR),
    used_at timestamp with time zone,

    user_id uuid NOT NULL REFERENCES app_auth.users(id)
        ON UPDATE NO ACTION
        ON DELETE CASCADE
);
--Supports token invalidation queries with `WHERE user_id = :userId AND used_at IS NULL`
CREATE INDEX IF NOT EXISTS email_verification_tokens_user_id_idx
    ON app_auth.email_verification_tokens(user_id);
--Supports expired-token cleanup queries with `WHERE expires_at < now()`
CREATE INDEX IF NOT EXISTS email_verification_tokens_expires_at_idx
    ON app_auth.email_verification_tokens(expires_at);

CREATE TABLE IF NOT EXISTS app_auth.password_reset_tokens(
    --Automatic PRIMARY KEY index used by queries that update or delete a password-reset token with `WHERE id = :tokenId`
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    --Automatic UNIQUE index used by password-reset confirmation queries with `WHERE token_hash = :tokenHash`
    token_hash varchar(64) UNIQUE NOT NULL,
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    expires_at timestamp with time zone NOT NULL DEFAULT (now() + INTERVAL '1' HOUR),
    used_at timestamp with time zone,

    user_id uuid NOT NULL REFERENCES app_auth.users(id)
        ON UPDATE NO ACTION
        ON DELETE CASCADE
);
--Supports token invalidation queries with `WHERE user_id = :userId AND used_at IS NULL`
CREATE INDEX IF NOT EXISTS password_reset_tokens_user_id_idx
    ON app_auth.password_reset_tokens(user_id);
--Supports expired-token cleanup queries with `WHERE expires_at < now()`
CREATE INDEX IF NOT EXISTS password_reset_tokens_expires_at_idx
    ON app_auth.password_reset_tokens(expires_at);


CREATE SCHEMA IF NOT EXISTS public;

CREATE TABLE IF NOT EXISTS public.recipes (
    --Automatic PRIMARY KEY index used by recipe detail, update, and delete queries with `WHERE id = :recipeId`
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    title varchar(60) NOT NULL,
    description text NOT NULL,
    side_notes text NOT NULL,
    difficulty smallint NOT NULL DEFAULT 1 CHECK (difficulty BETWEEN 1 AND 5),
    portions smallint NOT NULL DEFAULT 1,
    likes integer NOT NULL DEFAULT 0,
    cook_time varchar(20) NOT NULL DEFAULT '0',
    times_made integer NOT NULL DEFAULT 0,
    created_date timestamp with time zone NOT NULL DEFAULT now(),
    user_name varchar(60) NOT NULL,
    is_private boolean NOT NULL DEFAULT false,
    content jsonb NOT NULL DEFAULT
        '{
            "images": {},
            "ingredients": {},
            "instructions": {},
            "ingredientsOrder": [],
            "instructionsOrder": [],
            "heroImagesOrder": []
        }'::jsonb
        CHECK (jsonb_typeof(content) = 'object')
        CHECK (jsonb_typeof(content->'images') = 'object')
        CHECK (jsonb_typeof(content->'ingredients') = 'object')
        CHECK (jsonb_typeof(content->'instructions') = 'object')
        CHECK (jsonb_typeof(content->'ingredientsOrder') = 'array')
        CHECK (jsonb_typeof(content->'instructionsOrder') = 'array')
        CHECK (jsonb_typeof(content->'heroImagesOrder') = 'array'),

    user_id uuid NOT NULL REFERENCES app_auth.users(id)
        ON UPDATE NO ACTION
        ON DELETE CASCADE
);
--Supports the owner's dashboard query with `WHERE user_id = :userId ORDER BY name ASC, id ASC`, including private recipes
CREATE INDEX IF NOT EXISTS recipes_user_name_id_idx
    ON public.recipes(user_id, name ASC, id ASC);
--Supports fuzzy name searches with `WHERE name % :term` across public and owner-visible private recipes
CREATE INDEX IF NOT EXISTS recipes_name_trgm_idx
    ON public.recipes USING gin (name gin_trgm_ops);
--Supports the public popular-feed query with `WHERE is_private = false ORDER BY likes DESC, id DESC`
CREATE INDEX IF NOT EXISTS recipes_public_likes_id_idx
    ON public.recipes(likes DESC, id DESC)
    WHERE is_private = false;
--Supports the public alphabetical-feed query with `WHERE is_private = false ORDER BY name ASC, id ASC`
CREATE INDEX IF NOT EXISTS recipes_public_name_id_idx
    ON public.recipes(name ASC, id ASC)
    WHERE is_private = false;


CREATE TABLE IF NOT EXISTS public.recipe_likes(
    recipe_id uuid NOT NULL REFERENCES public.recipes(id)
        ON UPDATE NO ACTION
        ON DELETE CASCADE,
    user_id uuid NOT NULL REFERENCES app_auth.users(id)
        ON UPDATE NO ACTION
        ON DELETE CASCADE,
    created_at timestamp with time zone NOT NULL DEFAULT now(),

    --Automatic composite PRIMARY KEY index used by liked-state and unlike queries with `WHERE recipe_id = :recipeId AND user_id = :userId`
    PRIMARY KEY (recipe_id, user_id)
);
--Supports liked-recipe feed queries with `WHERE user_id = :userId ORDER BY created_at DESC, recipe_id DESC`
CREATE INDEX IF NOT EXISTS recipe_likes_user_created_idx
    ON public.recipe_likes(user_id, created_at DESC, recipe_id DESC);


