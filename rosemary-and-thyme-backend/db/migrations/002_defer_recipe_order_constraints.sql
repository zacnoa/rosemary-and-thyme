-- Run on existing databases; fresh databases get these definitions from schema.sql.
-- Replace all four constraints atomically, keeping the same keys and names.
BEGIN;

ALTER TABLE public.ingredients
    DROP CONSTRAINT ingredients_recipe_id_index_key,
    ADD CONSTRAINT ingredients_recipe_id_index_key
        UNIQUE (recipe_id, index) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.instructions
    DROP CONSTRAINT instructions_recipe_id_index_key,
    ADD CONSTRAINT instructions_recipe_id_index_key
        UNIQUE (recipe_id, index) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.hero_images
    DROP CONSTRAINT hero_images_recipe_id_index_key,
    ADD CONSTRAINT hero_images_recipe_id_index_key
        UNIQUE (recipe_id, index) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.instruction_images
    DROP CONSTRAINT instruction_images_instruction_id_index_key,
    ADD CONSTRAINT instruction_images_instruction_id_index_key
        UNIQUE (instruction_id, index) DEFERRABLE INITIALLY DEFERRED;

COMMIT;
