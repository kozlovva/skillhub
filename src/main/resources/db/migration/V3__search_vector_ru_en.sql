ALTER TABLE elements ADD COLUMN latest_changelog TEXT NOT NULL DEFAULT '';

DROP INDEX IF EXISTS idx_elements_search;
ALTER TABLE elements DROP COLUMN IF EXISTS search_vector;
DROP FUNCTION IF EXISTS elements_search_vector(text, text, text[]);

CREATE FUNCTION elements_search_vector(name TEXT, description TEXT, changelog TEXT, tags TEXT[])
RETURNS tsvector LANGUAGE sql IMMUTABLE PARALLEL SAFE AS $$
  SELECT setweight(to_tsvector('russian', coalesce(name, '')), 'A') ||
         setweight(to_tsvector('english', coalesce(name, '')), 'A') ||
         setweight(to_tsvector('russian', coalesce(description, '')), 'B') ||
         setweight(to_tsvector('english', coalesce(description, '')), 'B') ||
         setweight(to_tsvector('russian', array_to_string(coalesce(tags, '{}'::text[]), ' ')), 'C') ||
         setweight(to_tsvector('english', array_to_string(coalesce(tags, '{}'::text[]), ' ')), 'C') ||
         setweight(to_tsvector('russian', coalesce(changelog, '')), 'C') ||
         setweight(to_tsvector('english', coalesce(changelog, '')), 'C');
$$;

ALTER TABLE elements ADD COLUMN search_vector tsvector GENERATED ALWAYS AS (
  elements_search_vector(name, description, latest_changelog, tags)
) STORED;

CREATE INDEX idx_elements_search ON elements USING GIN (search_vector);
