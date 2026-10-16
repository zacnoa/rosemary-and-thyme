CREATE TABLE IF NOT EXISTS public.recipes (
    --Index created based on PRIMARY KEY
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name varchar(60) NOT NULL DEFAULT '',
    description text NOT NULL DEFAULT '',
    side_notes text NOT NULL DEFAULT '',
    difficulty smallint NOT NULL DEFAULT 1 CHECK (difficulty BETWEEN 1 AND 5),
    portions smallint NOT NULL DEFAULT 1,
    likes integer NOT NULL DEFAULT 0,
    cook_time varchar(20) NOT NULL DEFAULT '0',
    times_made integer NOT NULL DEFAULT 0,
    created_date date NOT NULL DEFAULT CURRENT_DATE,
    user_name varchar(100) NOT NULL,

    user_id uuid NOT NULL REFERENCES app_auth.users(id)
        ON UPDATE NO ACTION
        ON DELETE CASCADE
);
--Index when searching for all recipes belonging to certain user
CREATE INDEX recipes_user_id ON public.recipes(user_id);

CREATE TABLE IF NOT EXISTS public.ingredients (
    --Index created based on PRIMARY KEY
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name varchar(60) NOT NULL DEFAULT '',
    --Purposely left without a default because a default would need to be unique for
    --every ingredient that belongs to the same recipe
    index smallint NOT NULL,
    amount NUMERIC(4,2) NOT NULL DEFAULT 0.0,
    measuring_unit varchar(20) NOT NULL DEFAULT '',

    recipe_id uuid NOT NULL REFERENCES public.recipes(id)
);
--Index for joining all ingredients of a recipe and ensuring that no ingredient has the same index
CREATE UNIQUE INDEX ingredients_recipe_id_index_idx ON public.ingredients(recipe_id, index);


CREATE TABLE IF NOT EXISTS public.instructions(
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    --Purposely left without a default because a default would need to be unique for
    --every instruction that belongs to the same recipe
    index smallint NOT NULL,
    text text NOT NULL DEFAULT '',

    recipe_id uuid NOT NULL REFERENCES public.recipes(id)

);
--Index for joining all instruction of a recipe and ensuring that no instructions has the same index
CREATE UNIQUE INDEX instructions_recipe_id_index_idx ON public.instructions(recipe_id, index);

CREATE TABLE IF NOT EXISTS public.images(
    --Index based on PRIMARY KEY
    id uuid PRIMARY KEY DEFAULT  gen_random_uuid(),
    url text NOT NULL,

    recipe_id uuid NOT NULL REFERENCES public.recipes(id)

);

CREATE TABLE IF NOT EXISTS public.instruction_images(
    --Index based on PRIMARY KEY
    id uuid PRIMARY KEY REFERENCES public.images(id),
    instruction_id uuid NOT NULL REFERENCES public.instructions(id)
);

CREATE TABLE IF NOT EXISTS public.hero_images(
    --Index based on PRIMARY KEY
    id uuid PRIMARY KEY REFERENCES public.images(id),
    --Purposely left without a default because a default would need to be unique for
    --every hero image that belongs to the same recipe
    index smallint NOT NULL
);


