-- Ensure files uniqueness is scoped per user.

DO $$
DECLARE
    constraint_name text;
BEGIN
    SELECT c.conname INTO constraint_name
    FROM pg_constraint c
    JOIN pg_class t ON c.conrelid = t.oid
    JOIN pg_namespace n ON t.relnamespace = n.oid
    WHERE t.relname = 'files'
      AND n.nspname = 'app'
      AND c.contype = 'u'
      AND c.conkey = ARRAY[
          (SELECT attnum
           FROM pg_attribute
           WHERE attrelid = t.oid AND attname = 'sha256')
      ]::smallint[];

    IF constraint_name IS NOT NULL THEN
        EXECUTE format('ALTER TABLE app.files DROP CONSTRAINT %I', constraint_name);
    END IF;
END $$;

ALTER TABLE app.files
    ADD CONSTRAINT uq_files_user_sha256 UNIQUE (user_id, sha256);
