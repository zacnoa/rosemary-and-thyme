-- Run once against existing databases after deploying the transactional cleanup code.
-- images is the parent of both join tables, so deleting a recipe/instruction does
-- not cascade back to it; remove rows no longer referenced by either gallery.
DELETE FROM public.images i
WHERE NOT EXISTS (SELECT 1 FROM public.hero_images h WHERE h.image_id = i.id)
  AND NOT EXISTS (SELECT 1 FROM public.instruction_images ii WHERE ii.image_id = i.id);
