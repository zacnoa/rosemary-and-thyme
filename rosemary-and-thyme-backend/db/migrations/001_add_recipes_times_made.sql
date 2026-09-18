ALTER TABLE public.recipes
    ADD COLUMN IF NOT EXISTS times_made bigint NOT NULL DEFAULT 0;
