-- Add SUPERSEDED status support for statements and track superseded time

ALTER TABLE app.statements
    ADD COLUMN IF NOT EXISTS superseded_at TIMESTAMPTZ;

DO $$
DECLARE
    rec RECORD;
BEGIN
    FOR rec IN
        SELECT con.conname
        FROM pg_constraint con
                 JOIN pg_class rel ON rel.oid = con.conrelid
                 JOIN pg_namespace nsp ON nsp.oid = rel.relnamespace
        WHERE nsp.nspname = 'app'
          AND rel.relname = 'statements'
          AND con.contype = 'c'
          AND pg_get_constraintdef(con.oid) ILIKE '%status%'
    LOOP
        EXECUTE format('ALTER TABLE app.statements DROP CONSTRAINT IF EXISTS %I', rec.conname);
    END LOOP;
END $$;

ALTER TABLE app.statements
    ADD CONSTRAINT chk_statements_status
        CHECK (status IN ('DRAFT','REVIEWED','CONFIRMED','FAILED','SUPERSEDED'));

-- Timestamp when this statement was superseded by a new parsed version
COMMENT ON COLUMN app.statements.superseded_at IS 'Timestamp when superseded by new parsed version';
