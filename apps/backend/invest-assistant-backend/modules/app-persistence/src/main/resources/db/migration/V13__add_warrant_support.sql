-- Add WARRANT asset_type and warrant_profiles table

-- Extend instruments.asset_type constraint
ALTER TABLE app.instruments
    DROP CONSTRAINT IF EXISTS chk_instruments_asset_type;

ALTER TABLE app.instruments
    ADD CONSTRAINT chk_instruments_asset_type
        CHECK (asset_type IN ('STOCK', 'ETF', 'WARRANT'));

-- Warrant profile (minimal v1 fields)
CREATE TABLE IF NOT EXISTS app.warrant_profiles (
    instrument_id     BIGINT PRIMARY KEY REFERENCES app.instruments(id) ON DELETE CASCADE,
    underlying_symbol TEXT,
    expiry_date       DATE,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_warrant_profiles_underlying ON app.warrant_profiles(underlying_symbol);

CREATE TRIGGER tg_warrant_profiles_updated_at
    BEFORE UPDATE ON app.warrant_profiles
    FOR EACH ROW EXECUTE FUNCTION app.tg_set_updated_at();

COMMENT ON TABLE app.warrant_profiles IS '權證基本資料';
COMMENT ON COLUMN app.warrant_profiles.instrument_id IS '對應商品 ID';
COMMENT ON COLUMN app.warrant_profiles.underlying_symbol IS '標的代號';
COMMENT ON COLUMN app.warrant_profiles.expiry_date IS '到期日';
