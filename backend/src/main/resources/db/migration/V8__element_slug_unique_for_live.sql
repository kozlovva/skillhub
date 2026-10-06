-- Slug uniqueness must only apply to live (non soft-deleted) elements,
-- otherwise a deleted element permanently blocks its slug.
ALTER TABLE elements DROP CONSTRAINT IF EXISTS elements_slug_key;
CREATE UNIQUE INDEX idx_elements_slug_live ON elements (slug) WHERE deleted_at IS NULL;
