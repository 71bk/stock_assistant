-- =========================================
-- R__test_instruments.sql
-- 開發/測試用商品資料（Repeatable Migration）
-- 僅在 dev profile 載入
-- =========================================

-- 台積電
INSERT INTO app.instruments (market_id, exchange_id, ticker, name_zh, name_en, currency, symbol_key)
SELECT m.id, e.id, '2330', '台積電', 'Taiwan Semiconductor', 'TWD', 'TW:XTAI:2330'
FROM app.markets m
         JOIN app.exchanges e ON e.market_id = m.id AND e.mic = 'XTAI'
WHERE m.code = 'TW'
ON CONFLICT (symbol_key) DO NOTHING;

-- 鴻海
INSERT INTO app.instruments (market_id, exchange_id, ticker, name_zh, name_en, currency, symbol_key)
SELECT m.id, e.id, '2317', '鴻海', 'Hon Hai Precision', 'TWD', 'TW:XTAI:2317'
FROM app.markets m
         JOIN app.exchanges e ON e.market_id = m.id AND e.mic = 'XTAI'
WHERE m.code = 'TW'
ON CONFLICT (symbol_key) DO NOTHING;

-- Apple
INSERT INTO app.instruments (market_id, exchange_id, ticker, name_zh, name_en, currency, symbol_key)
SELECT m.id, e.id, 'AAPL', '蘋果', 'Apple Inc.', 'USD', 'US:XNAS:AAPL'
FROM app.markets m
         JOIN app.exchanges e ON e.market_id = m.id AND e.mic = 'XNAS'
WHERE m.code = 'US'
ON CONFLICT (symbol_key) DO NOTHING;

-- Tesla
INSERT INTO app.instruments (market_id, exchange_id, ticker, name_zh, name_en, currency, symbol_key)
SELECT m.id, e.id, 'TSLA', '特斯拉', 'Tesla Inc.', 'USD', 'US:XNAS:TSLA'
FROM app.markets m
         JOIN app.exchanges e ON e.market_id = m.id AND e.mic = 'XNAS'
WHERE m.code = 'US'
ON CONFLICT (symbol_key) DO NOTHING;

-- Microsoft
INSERT INTO app.instruments (market_id, exchange_id, ticker, name_zh, name_en, currency, symbol_key)
SELECT m.id, e.id, 'MSFT', '微軟', 'Microsoft Corporation', 'USD', 'US:XNAS:MSFT'
FROM app.markets m
         JOIN app.exchanges e ON e.market_id = m.id AND e.mic = 'XNAS'
WHERE m.code = 'US'
ON CONFLICT (symbol_key) DO NOTHING;

-- Berkshire Hathaway B
INSERT INTO app.instruments (market_id, exchange_id, ticker, name_zh, name_en, currency, symbol_key)
SELECT m.id, e.id, 'BRK.B', '波克夏B', 'Berkshire Hathaway B', 'USD', 'US:XNYS:BRK.B'
FROM app.markets m
         JOIN app.exchanges e ON e.market_id = m.id AND e.mic = 'XNYS'
WHERE m.code = 'US'
ON CONFLICT (symbol_key) DO NOTHING;

-- 別名：BRK-B
INSERT INTO app.instrument_aliases (instrument_id, source, alias_ticker)
SELECT i.id, 'MANUAL', 'BRK-B'
FROM app.instruments i WHERE i.symbol_key = 'US:XNYS:BRK.B'
ON CONFLICT (source, alias_ticker) DO NOTHING;

-- 別名：BRKB
INSERT INTO app.instrument_aliases (instrument_id, source, alias_ticker)
SELECT i.id, 'OCR', 'BRKB'
FROM app.instruments i WHERE i.symbol_key = 'US:XNYS:BRK.B'
ON CONFLICT (source, alias_ticker) DO NOTHING;
