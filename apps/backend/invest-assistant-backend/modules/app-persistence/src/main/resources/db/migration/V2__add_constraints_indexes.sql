-- =========================================
-- V2__add_constraints_indexes.sql
-- 補充 V1 缺少的約束和索引（搜尋 API 用）
-- =========================================

-- 1) instruments: 同一交易所不應有重複的 ticker
CREATE UNIQUE INDEX IF NOT EXISTS idx_instruments_exchange_ticker_unique
    ON app.instruments(exchange_id, ticker);

-- 2) instrument_aliases: 搜尋 API 會用 alias 做模糊查詢，需要索引加速
CREATE INDEX IF NOT EXISTS idx_instrument_aliases_alias
    ON app.instrument_aliases(alias_ticker);

-- 3) instruments: ticker 搜尋索引（補充 name 搜尋）
CREATE INDEX IF NOT EXISTS idx_instruments_ticker
    ON app.instruments(ticker);

-- 4) instruments: name 搜尋索引（中英文）
CREATE INDEX IF NOT EXISTS idx_instruments_name_zh
    ON app.instruments(name_zh);
CREATE INDEX IF NOT EXISTS idx_instruments_name_en
    ON app.instruments(name_en);
