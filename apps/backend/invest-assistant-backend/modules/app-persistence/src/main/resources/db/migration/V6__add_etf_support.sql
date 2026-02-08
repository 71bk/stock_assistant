-- V6: Add ETF support
-- Description: Add asset_type field to instruments and create etf_profiles table

-- ============================================================
-- 1. Add asset_type column to app.instruments
-- ============================================================
ALTER TABLE app.instruments
    ADD COLUMN IF NOT EXISTS asset_type TEXT DEFAULT 'STOCK' NOT NULL;

-- Add check constraint for asset_type
ALTER TABLE app.instruments
    ADD CONSTRAINT chk_instruments_asset_type 
    CHECK (asset_type IN ('STOCK', 'ETF'));

-- Update existing data to STOCK (if any nulls exist)
UPDATE app.instruments
SET asset_type = 'STOCK'
WHERE asset_type IS NULL;

COMMENT ON COLUMN app.instruments.asset_type IS '資產類型：STOCK（股票）, ETF（指數股票型基金）';

-- ============================================================
-- 2. Create app.etf_profiles table
-- ============================================================
CREATE TABLE IF NOT EXISTS app.etf_profiles (
    instrument_id BIGINT PRIMARY KEY,
    underlying_type TEXT NOT NULL,
    underlying_name TEXT NOT NULL,
    as_of_date DATE,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP NOT NULL,
    
    CONSTRAINT fk_etf_profiles_instrument 
        FOREIGN KEY (instrument_id) 
        REFERENCES app.instruments(id) 
        ON DELETE CASCADE
);

-- Add check constraint for underlying_type
ALTER TABLE app.etf_profiles
    ADD CONSTRAINT chk_etf_profiles_underlying_type 
    CHECK (underlying_type IN ('INDEX', 'THEMATIC', 'FACTOR', 'ACTIVE', 'OTHER'));

-- Add index for querying by underlying_type
CREATE INDEX IF NOT EXISTS idx_etf_profiles_underlying_type 
    ON app.etf_profiles(underlying_type);

-- Comments
COMMENT ON TABLE app.etf_profiles IS 'ETF 專屬資訊表，儲存 ETF 的追蹤標的資訊';
COMMENT ON COLUMN app.etf_profiles.instrument_id IS '商品 ID（僅 ETF，參照 instruments.id）';
COMMENT ON COLUMN app.etf_profiles.underlying_type IS '標的類型：INDEX（指數型）, THEMATIC（主題型）, FACTOR（因子型）, ACTIVE（主動型）, OTHER（其他）';
COMMENT ON COLUMN app.etf_profiles.underlying_name IS '標的名稱，例如：「S&P 500 Index」、「全球AI×智能電動車」';
COMMENT ON COLUMN app.etf_profiles.as_of_date IS '資料更新日期（可選）';
