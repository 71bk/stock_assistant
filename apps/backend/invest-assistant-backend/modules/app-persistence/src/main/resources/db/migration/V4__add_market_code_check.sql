DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'ck_markets_code'
          AND conrelid = 'app.markets'::regclass
    ) THEN
        ALTER TABLE app.markets
            ADD CONSTRAINT ck_markets_code CHECK (code IN ('TW', 'US'));
    END IF;
END $$;
