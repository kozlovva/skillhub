CREATE OR REPLACE FUNCTION elements_search_vector(name TEXT, description TEXT, tags TEXT[])
RETURNS tsvector LANGUAGE sql IMMUTABLE PARALLEL SAFE AS $$
  SELECT setweight(to_tsvector('simple', coalesce(name, '')), 'A') ||
         setweight(to_tsvector('simple', coalesce(description, '')), 'B') ||
         setweight(to_tsvector('simple', array_to_string(tags, ' ')), 'C');
$$;

ALTER TABLE elements ADD COLUMN search_vector tsvector GENERATED ALWAYS AS (
  elements_search_vector(name, description, tags)
) STORED;

CREATE INDEX idx_elements_search ON elements USING GIN (search_vector);
