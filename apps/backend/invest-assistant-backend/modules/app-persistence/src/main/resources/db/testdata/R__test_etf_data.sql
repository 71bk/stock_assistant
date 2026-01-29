-- ETF Test Data
-- Description: Insert test data for ETF instruments (00926, VOO)
-- Usage: Run manually or via test data loading script
-- Last Updated: 2026-01-11 02:29:30

-- ============================================================
-- Insert ETF test data
-- ============================================================

-- Taiwan ETF: 00926 (永豐全球AI智能電動車ETF)
DO $$
DECLARE
    v_market_id BIGINT;
    v_exchange_id BIGINT;
    v_instrument_id BIGINT;
BEGIN
    -- Get market and exchange IDs
    SELECT id INTO v_market_id FROM app.markets WHERE code = 'TW';
    SELECT id INTO v_exchange_id FROM app.exchanges WHERE mic = 'XTAI';
    
    -- Skip if base data not found
    IF v_market_id IS NULL OR v_exchange_id IS NULL THEN
        RAISE NOTICE '⚠ Skipping ETF 00926: Base data (markets/exchanges) not found';
        RETURN;
    END IF;
    
    -- Insert instrument (if not exists)
    INSERT INTO app.instruments (
        market_id, 
        exchange_id, 
        ticker, 
        symbol_key, 
        name_zh, 
        name_en, 
        currency, 
        asset_type, 
        status
    ) VALUES (
        v_market_id,
        v_exchange_id,
        '00926',
        'TW:XTAI:00926',
        '永豐全球AI智能電動車ETF',
        'SinoPac Global AI & Smart Electric Vehicle ETF',
        'TWD',
        'ETF',
        'ACTIVE'
    )
    ON CONFLICT (symbol_key) 
    DO UPDATE SET 
        asset_type = 'ETF',
        name_zh = EXCLUDED.name_zh,
        name_en = EXCLUDED.name_en,
        updated_at = CURRENT_TIMESTAMP
    RETURNING id INTO v_instrument_id;
    
    -- Get instrument_id if already exists
    IF v_instrument_id IS NULL THEN
        SELECT id INTO v_instrument_id FROM app.instruments WHERE symbol_key = 'TW:XTAI:00926';
    END IF;
    
    -- Insert ETF profile (if not exists)
    INSERT INTO app.etf_profiles (
        instrument_id,
        underlying_type,
        underlying_name,
        updated_at
    ) VALUES (
        v_instrument_id,
        'THEMATIC',
        '全球AI × 智能電動車',
        CURRENT_TIMESTAMP
    )
    ON CONFLICT (instrument_id) 
    DO UPDATE SET 
        underlying_type = EXCLUDED.underlying_type,
        underlying_name = EXCLUDED.underlying_name,
        updated_at = CURRENT_TIMESTAMP;
    
    RAISE NOTICE '✓ ETF 00926 data inserted/updated successfully';
END $$;

-- US ETF: VOO (Vanguard S&P 500 ETF)
DO $$
DECLARE
    v_market_id BIGINT;
    v_exchange_id BIGINT;
    v_instrument_id BIGINT;
BEGIN
    -- Get market and exchange IDs
    SELECT id INTO v_market_id FROM app.markets WHERE code = 'US';
    SELECT id INTO v_exchange_id FROM app.exchanges WHERE mic = 'XNAS';
    
    -- Skip if base data not found
    IF v_market_id IS NULL OR v_exchange_id IS NULL THEN
        RAISE NOTICE '⚠ Skipping ETF VOO: Base data (markets/exchanges) not found';
        RETURN;
    END IF;
    
    -- Insert instrument (if not exists)
    INSERT INTO app.instruments (
        market_id, 
        exchange_id, 
        ticker, 
        symbol_key, 
        name_zh, 
        name_en, 
        currency, 
        asset_type, 
        status
    ) VALUES (
        v_market_id,
        v_exchange_id,
        'VOO',
        'US:XNAS:VOO',
        'Vanguard標普500 ETF',
        'Vanguard S&P 500 ETF',
        'USD',
        'ETF',
        'ACTIVE'
    )
    ON CONFLICT (symbol_key) 
    DO UPDATE SET 
        asset_type = 'ETF',
        name_zh = EXCLUDED.name_zh,
        name_en = EXCLUDED.name_en,
        updated_at = CURRENT_TIMESTAMP
    RETURNING id INTO v_instrument_id;
    
    -- Get instrument_id if already exists
    IF v_instrument_id IS NULL THEN
        SELECT id INTO v_instrument_id FROM app.instruments WHERE symbol_key = 'US:XNAS:VOO';
    END IF;
    
    -- Insert ETF profile (if not exists)
    INSERT INTO app.etf_profiles (
        instrument_id,
        underlying_type,
        underlying_name,
        updated_at
    ) VALUES (
        v_instrument_id,
        'INDEX',
        'S&P 500 Index',
        CURRENT_TIMESTAMP
    )
    ON CONFLICT (instrument_id) 
    DO UPDATE SET 
        underlying_type = EXCLUDED.underlying_type,
        underlying_name = EXCLUDED.underlying_name,
        updated_at = CURRENT_TIMESTAMP;
    
    RAISE NOTICE '✓ ETF VOO data inserted/updated successfully';
END $$;

-- ============================================================
-- Additional popular ETFs (commented out - uncomment if needed)
-- ============================================================

-- US ETF: QQQ (Invesco QQQ Trust)
/*
DO $$
DECLARE
    v_market_id BIGINT;
    v_exchange_id BIGINT;
    v_instrument_id BIGINT;
BEGIN
    SELECT id INTO v_market_id FROM app.markets WHERE code = 'US';
    SELECT id INTO v_exchange_id FROM app.exchanges WHERE code = 'XNAS';
    
    INSERT INTO app.instruments (
        market_id, exchange_id, ticker, symbol_key, 
        name_zh, name_en, currency, asset_type, status
    ) VALUES (
        v_market_id, v_exchange_id, 'QQQ', 'US:XNAS:QQQ',
        'Invesco QQQ信託', 'Invesco QQQ Trust',
        'USD', 'ETF', 'ACTIVE'
    )
    ON CONFLICT (symbol_key) DO UPDATE SET asset_type = 'ETF'
    RETURNING id INTO v_instrument_id;
    
    IF v_instrument_id IS NULL THEN
        SELECT id INTO v_instrument_id FROM app.instruments WHERE symbol_key = 'US:XNAS:QQQ';
    END IF;
    
    INSERT INTO app.etf_profiles (instrument_id, underlying_type, underlying_name, updated_at)
    VALUES (v_instrument_id, 'INDEX', 'NASDAQ-100 Index', CURRENT_TIMESTAMP)
    ON CONFLICT (instrument_id) DO UPDATE SET 
        underlying_type = EXCLUDED.underlying_type,
        underlying_name = EXCLUDED.underlying_name,
        updated_at = CURRENT_TIMESTAMP;
        
    RAISE NOTICE '✓ ETF QQQ data inserted/updated successfully';
END $$;
*/

-- TW ETF: 0050 (元大台灣50)
/*
DO $$
DECLARE
    v_market_id BIGINT;
    v_exchange_id BIGINT;
    v_instrument_id BIGINT;
BEGIN
    SELECT id INTO v_market_id FROM app.markets WHERE code = 'TW';
    SELECT id INTO v_exchange_id FROM app.exchanges WHERE code = 'XTAI';
    
    INSERT INTO app.instruments (
        market_id, exchange_id, ticker, symbol_key, 
        name_zh, name_en, currency, asset_type, status
    ) VALUES (
        v_market_id, v_exchange_id, '0050', 'TW:XTAI:0050',
        '元大台灣50', 'Yuanta Taiwan Top 50 ETF',
        'TWD', 'ETF', 'ACTIVE'
    )
    ON CONFLICT (symbol_key) DO UPDATE SET asset_type = 'ETF'
    RETURNING id INTO v_instrument_id;
    
    IF v_instrument_id IS NULL THEN
        SELECT id INTO v_instrument_id FROM app.instruments WHERE symbol_key = 'TW:XTAI:0050';
    END IF;
    
    INSERT INTO app.etf_profiles (instrument_id, underlying_type, underlying_name, updated_at)
    VALUES (v_instrument_id, 'INDEX', '台灣50指數', CURRENT_TIMESTAMP)
    ON CONFLICT (instrument_id) DO UPDATE SET 
        underlying_type = EXCLUDED.underlying_type,
        underlying_name = EXCLUDED.underlying_name,
        updated_at = CURRENT_TIMESTAMP;
        
    RAISE NOTICE '✓ ETF 0050 data inserted/updated successfully';
END $$;
*/
