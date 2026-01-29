-- =========================================
-- V3__seed_base_data.sql
-- 基礎種子資料：市場、交易所、範例股票
-- =========================================

-- 1) 市場 (TW / US)
INSERT INTO app.markets (code, name, timezone, default_currency)
VALUES
    ('TW', 'Taiwan', 'Asia/Taipei', 'TWD'),
    ('US', 'United States', 'America/New_York', 'USD')
ON CONFLICT (code) DO NOTHING;

-- 2) 交易所
-- 台灣
INSERT INTO app.exchanges (market_id, mic, code, name)
SELECT m.id, 'XTAI', 'TWSE', '台灣證券交易所'
FROM app.markets m WHERE m.code = 'TW'
ON CONFLICT (market_id, mic) DO NOTHING;

INSERT INTO app.exchanges (market_id, mic, code, name)
SELECT m.id, 'ROCO', 'TPEx', '台北證券櫃檯買賣中心'
FROM app.markets m WHERE m.code = 'TW'
ON CONFLICT (market_id, mic) DO NOTHING;

-- 美國
INSERT INTO app.exchanges (market_id, mic, code, name)
SELECT m.id, 'XNAS', 'NASDAQ', 'NASDAQ Stock Exchange'
FROM app.markets m WHERE m.code = 'US'
ON CONFLICT (market_id, mic) DO NOTHING;

INSERT INTO app.exchanges (market_id, mic, code, name)
SELECT m.id, 'XNYS', 'NYSE', 'New York Stock Exchange'
FROM app.markets m WHERE m.code = 'US'
ON CONFLICT (market_id, mic) DO NOTHING;
