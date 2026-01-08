-- =========================================
-- 投資助理平台 - Database DDL (PostgreSQL)
-- Version: v1.1 (app + vector schemas, pgvector)
-- Notes:
--   1) 所有時間一律存 UTC：timestamptz
--   2) 匯入（OCR/手輸）先進 staging（statements + statement_*），Confirm 後寫入 stock_trades / user_positions
--   3) 民國日期（YYYMMDD / YYYMM）應在匯入層轉為西元 DATE 後再入庫
--   4) pgvector embedding 維度預設 1536（可依你選的 embedding model 調整）
-- =========================================

BEGIN;

-- --- Extensions -------------------------------------------------------------
CREATE EXTENSION IF NOT EXISTS pgcrypto;   -- gen_random_uuid()
CREATE EXTENSION IF NOT EXISTS vector;     -- pgvector type

-- --- Schemas ----------------------------------------------------------------
CREATE SCHEMA IF NOT EXISTS app;
CREATE SCHEMA IF NOT EXISTS vector;

-- --- Helper: updated_at trigger --------------------------------------------
CREATE OR REPLACE FUNCTION app.tg_set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
  NEW.updated_at = now();
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- =========================================
-- 1) Auth / Users
-- =========================================

CREATE TABLE IF NOT EXISTS app.users (
  id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  email           TEXT NOT NULL UNIQUE,
  google_sub      TEXT NOT NULL UNIQUE,
  display_name    TEXT,
  picture_url     TEXT,                                        -- Google 頭像 URL
  status          TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','SUSPENDED','PENDING')),
  last_login_at   TIMESTAMPTZ,                                 -- 最後登入時間
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TRIGGER tg_users_updated_at
BEFORE UPDATE ON app.users
FOR EACH ROW EXECUTE FUNCTION app.tg_set_updated_at();

CREATE TABLE IF NOT EXISTS app.user_settings (
  user_id         BIGINT PRIMARY KEY REFERENCES app.users(id) ON DELETE CASCADE,
  base_currency   CHAR(3) NOT NULL DEFAULT 'TWD',
  display_timezone TEXT NOT NULL DEFAULT 'Asia/Taipei',
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TRIGGER tg_user_settings_updated_at
BEFORE UPDATE ON app.user_settings
FOR EACH ROW EXECUTE FUNCTION app.tg_set_updated_at();

-- =========================================
-- 2) Multi-market master data (TW + US)
-- =========================================

CREATE TABLE IF NOT EXISTS app.markets (
  id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  code             TEXT NOT NULL UNIQUE,                  -- 'TW', 'US'
  name             TEXT NOT NULL,                         -- 'Taiwan', 'United States'
  timezone         TEXT NOT NULL,                         -- 'Asia/Taipei', 'America/New_York'
  default_currency CHAR(3) NOT NULL,                      -- 'TWD', 'USD'
  created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TRIGGER tg_markets_updated_at
BEFORE UPDATE ON app.markets
FOR EACH ROW EXECUTE FUNCTION app.tg_set_updated_at();

CREATE TABLE IF NOT EXISTS app.exchanges (
  id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  market_id   BIGINT NOT NULL REFERENCES app.markets(id) ON DELETE RESTRICT,
  mic         TEXT NOT NULL,                              -- 'XNAS', 'XNYS', 'TWSE', 'TPEx'
  code        TEXT,                                       -- short code if needed
  name        TEXT NOT NULL,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (market_id, mic)
);

CREATE INDEX IF NOT EXISTS idx_exchanges_market ON app.exchanges(market_id);

CREATE TRIGGER tg_exchanges_updated_at
BEFORE UPDATE ON app.exchanges
FOR EACH ROW EXECUTE FUNCTION app.tg_set_updated_at();

CREATE TABLE IF NOT EXISTS app.instruments (
  id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  market_id    BIGINT NOT NULL REFERENCES app.markets(id) ON DELETE RESTRICT,
  exchange_id  BIGINT NOT NULL REFERENCES app.exchanges(id) ON DELETE RESTRICT,
  ticker       TEXT NOT NULL,                             -- '2330', 'AAPL', 'BRK.B'
  name_zh      TEXT,
  name_en      TEXT,
  currency     CHAR(3) NOT NULL,                          -- usually same as market default, but keep explicit
  status       TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','DELISTED','SUSPENDED')),
  symbol_key   TEXT NOT NULL,                             -- 'TW:TWSE:2330' / 'US:XNAS:AAPL'
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (symbol_key)
);

CREATE INDEX IF NOT EXISTS idx_instruments_market_ticker ON app.instruments(market_id, ticker);
CREATE UNIQUE INDEX IF NOT EXISTS idx_instruments_exchange_ticker_unique ON app.instruments(exchange_id, ticker);
CREATE INDEX IF NOT EXISTS idx_instruments_ticker ON app.instruments(ticker);
CREATE INDEX IF NOT EXISTS idx_instruments_name_zh ON app.instruments(name_zh);
CREATE INDEX IF NOT EXISTS idx_instruments_name_en ON app.instruments(name_en);

CREATE TRIGGER tg_instruments_updated_at
BEFORE UPDATE ON app.instruments
FOR EACH ROW EXECUTE FUNCTION app.tg_set_updated_at();

CREATE TABLE IF NOT EXISTS app.instrument_aliases (
  id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  instrument_id BIGINT NOT NULL REFERENCES app.instruments(id) ON DELETE CASCADE,
  source        TEXT NOT NULL,                             -- 'OCR', 'MANUAL', 'DATA_VENDOR_X'
  alias_ticker  TEXT NOT NULL,                             -- 'BRK-B' for 'BRK.B' etc
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (source, alias_ticker),
  UNIQUE (instrument_id, source, alias_ticker)
);

CREATE INDEX IF NOT EXISTS idx_instrument_aliases_instrument ON app.instrument_aliases(instrument_id);
CREATE INDEX IF NOT EXISTS idx_instrument_aliases_alias ON app.instrument_aliases(alias_ticker);

-- =========================================
-- 3) Market data cacheable tables (prices / fx)
-- =========================================

CREATE TABLE IF NOT EXISTS app.prices (
  instrument_id  BIGINT NOT NULL REFERENCES app.instruments(id) ON DELETE CASCADE,
  time_interval  TEXT NOT NULL CHECK (time_interval IN ('1m','5m','15m','1h','1d','1w','1mo')),
  ts_utc         TIMESTAMPTZ NOT NULL,
  open           NUMERIC(20, 8) NOT NULL,
  high           NUMERIC(20, 8) NOT NULL,
  low            NUMERIC(20, 8) NOT NULL,
  close          NUMERIC(20, 8) NOT NULL,
  volume         NUMERIC(24, 6),
  source         TEXT,                                          -- 資料來源（yahoo/fugle/...）
  PRIMARY KEY (instrument_id, time_interval, ts_utc)
);

CREATE INDEX IF NOT EXISTS idx_prices_ts ON app.prices(ts_utc);

CREATE TABLE IF NOT EXISTS app.fx_rates (
  base_currency  CHAR(3) NOT NULL,                         -- e.g. 'TWD'
  quote_currency CHAR(3) NOT NULL,                         -- e.g. 'USD'
  ts_utc         TIMESTAMPTZ NOT NULL,
  rate           NUMERIC(20, 8) NOT NULL CHECK (rate > 0),
  PRIMARY KEY (base_currency, quote_currency, ts_utc)
);

CREATE INDEX IF NOT EXISTS idx_fx_rates_ts ON app.fx_rates(ts_utc);

-- =========================================
-- 4) Corporate actions (min: SPLIT)
-- =========================================

CREATE TABLE IF NOT EXISTS app.corporate_actions (
  id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  instrument_id  BIGINT NOT NULL REFERENCES app.instruments(id) ON DELETE CASCADE,
  action_type    TEXT NOT NULL CHECK (action_type IN ('SPLIT','DIVIDEND','RENAME','MERGER','SPINOFF','OTHER')),
  ex_date        DATE NOT NULL,
  effective_date DATE,
  payload        JSONB NOT NULL DEFAULT '{}'::jsonb,        -- SPLIT: { "ratio_from": 1, "ratio_to": 10 }
  created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_corp_actions_instrument_exdate ON app.corporate_actions(instrument_id, ex_date);

CREATE TRIGGER tg_corporate_actions_updated_at
BEFORE UPDATE ON app.corporate_actions
FOR EACH ROW EXECUTE FUNCTION app.tg_set_updated_at();

-- =========================================
-- 5) Portfolios / Accounts
-- =========================================

CREATE TABLE IF NOT EXISTS app.portfolios (
  id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  user_id       BIGINT NOT NULL REFERENCES app.users(id) ON DELETE CASCADE,
  name          TEXT NOT NULL DEFAULT 'Main',
  base_currency CHAR(3) NOT NULL DEFAULT 'TWD',
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_portfolios_user ON app.portfolios(user_id);

CREATE TRIGGER tg_portfolios_updated_at
BEFORE UPDATE ON app.portfolios
FOR EACH ROW EXECUTE FUNCTION app.tg_set_updated_at();

CREATE TABLE IF NOT EXISTS app.accounts (
  id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  portfolio_id BIGINT NOT NULL REFERENCES app.portfolios(id) ON DELETE CASCADE,
  name         TEXT NOT NULL,                              -- e.g. 'Sinopac-1', 'InteractiveBrokers'
  broker       TEXT,                                       -- optional
  currency     CHAR(3),                                    -- optional; if null => portfolio/base currency
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (portfolio_id, name)
);

CREATE INDEX IF NOT EXISTS idx_accounts_portfolio ON app.accounts(portfolio_id);

CREATE TRIGGER tg_accounts_updated_at
BEFORE UPDATE ON app.accounts
FOR EACH ROW EXECUTE FUNCTION app.tg_set_updated_at();

-- =========================================
-- 6) Core trading / positions / cash
-- =========================================

CREATE TABLE IF NOT EXISTS app.stock_trades (
  id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  user_id        BIGINT NOT NULL REFERENCES app.users(id) ON DELETE CASCADE,
  portfolio_id   BIGINT NOT NULL REFERENCES app.portfolios(id) ON DELETE CASCADE,
  account_id     BIGINT REFERENCES app.accounts(id) ON DELETE SET NULL,
  instrument_id  BIGINT NOT NULL REFERENCES app.instruments(id) ON DELETE RESTRICT,

  trade_date     DATE NOT NULL,                            -- 西元日期
  settlement_date DATE,
  side           TEXT NOT NULL CHECK (side IN ('BUY','SELL')),
  quantity       NUMERIC(20, 6) NOT NULL CHECK (quantity > 0),
  price          NUMERIC(20, 8) NOT NULL CHECK (price >= 0),
  currency       CHAR(3) NOT NULL,

  gross_amount   NUMERIC(20, 6),
  fee            NUMERIC(20, 6),
  tax            NUMERIC(20, 6),
  net_amount     NUMERIC(20, 6),                           -- 建議：買為負、賣為正（或統一規則但要一致）

  source         TEXT NOT NULL DEFAULT 'MANUAL' CHECK (source IN ('OCR','MANUAL','API','MIGRATION')),
  source_ref_id  BIGINT,                                   -- statement_trades.id (confirm 後可回填)
  row_hash       CHAR(64) NOT NULL,                         -- sha256(...)
  created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),

  UNIQUE (row_hash)
);

CREATE INDEX IF NOT EXISTS idx_stock_trades_portfolio_date ON app.stock_trades(portfolio_id, trade_date DESC);
CREATE INDEX IF NOT EXISTS idx_stock_trades_instrument_date ON app.stock_trades(instrument_id, trade_date DESC);

CREATE TRIGGER tg_stock_trades_updated_at
BEFORE UPDATE ON app.stock_trades
FOR EACH ROW EXECUTE FUNCTION app.tg_set_updated_at();

-- positions is a cache/snapshot derived from stock_trades (avg cost, qty, etc.)
CREATE TABLE IF NOT EXISTS app.user_positions (
  portfolio_id      BIGINT NOT NULL REFERENCES app.portfolios(id) ON DELETE CASCADE,
  instrument_id     BIGINT NOT NULL REFERENCES app.instruments(id) ON DELETE RESTRICT,

  total_quantity    NUMERIC(20, 6) NOT NULL DEFAULT 0,
  avg_cost_native   NUMERIC(20, 8),                         -- 加權平均成本
  currency          CHAR(3) NOT NULL,                        -- native currency of cost
  updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),

  PRIMARY KEY (portfolio_id, instrument_id)
);

CREATE INDEX IF NOT EXISTS idx_positions_instrument ON app.user_positions(instrument_id);

CREATE TABLE IF NOT EXISTS app.cash_ledger (
  id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  user_id       BIGINT NOT NULL REFERENCES app.users(id) ON DELETE CASCADE,
  portfolio_id  BIGINT NOT NULL REFERENCES app.portfolios(id) ON DELETE CASCADE,
  account_id    BIGINT REFERENCES app.accounts(id) ON DELETE SET NULL,

  ts_utc        TIMESTAMPTZ NOT NULL DEFAULT now(),
  currency      CHAR(3) NOT NULL,
  amount        NUMERIC(20, 6) NOT NULL,                    -- +in / -out
  entry_type    TEXT NOT NULL CHECK (entry_type IN ('DEPOSIT','WITHDRAW','TRADE_NET','FEE','TAX','DIVIDEND','INTEREST','ADJUSTMENT')),
  ref_trade_id  BIGINT REFERENCES app.stock_trades(id) ON DELETE SET NULL,
  meta          JSONB NOT NULL DEFAULT '{}'::jsonb
);

CREATE INDEX IF NOT EXISTS idx_cash_ledger_portfolio_ts ON app.cash_ledger(portfolio_id, ts_utc DESC);

CREATE TABLE IF NOT EXISTS app.portfolio_valuations (
  portfolio_id   BIGINT NOT NULL REFERENCES app.portfolios(id) ON DELETE CASCADE,
  as_of_date     DATE NOT NULL,
  base_currency  CHAR(3) NOT NULL,
  total_value    NUMERIC(20, 6) NOT NULL,
  cash_value     NUMERIC(20, 6) NOT NULL DEFAULT 0,
  positions_value NUMERIC(20, 6) NOT NULL DEFAULT 0,
  created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (portfolio_id, as_of_date)
);

-- =========================================
-- 7) Files + OCR jobs + Import staging (OCR/MANUAL)
-- =========================================

CREATE TABLE IF NOT EXISTS app.files (
  id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  user_id       BIGINT NOT NULL REFERENCES app.users(id) ON DELETE CASCADE,
  provider      TEXT NOT NULL CHECK (provider IN ('local','s3','minio')),
  bucket        TEXT,
  object_key    TEXT NOT NULL,
  sha256        CHAR(64) NOT NULL,
  size_bytes    BIGINT NOT NULL CHECK (size_bytes >= 0),
  content_type  TEXT NOT NULL,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (sha256)
);

CREATE INDEX IF NOT EXISTS idx_files_user_created ON app.files(user_id, created_at DESC);

-- statements: one import batch (OCR or manual text)
CREATE TABLE IF NOT EXISTS app.statements (
  id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  user_id       BIGINT NOT NULL REFERENCES app.users(id) ON DELETE CASCADE,
  portfolio_id  BIGINT NOT NULL REFERENCES app.portfolios(id) ON DELETE CASCADE,
  source        TEXT NOT NULL CHECK (source IN ('OCR','MANUAL')),
  file_id       BIGINT REFERENCES app.files(id) ON DELETE SET NULL,  -- OCR only
  status        TEXT NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT','REVIEWED','CONFIRMED','FAILED')),
  period_start  DATE,
  period_end    DATE,
  raw_text      TEXT,                                       -- optional: OCR raw text / manual input raw text
  parsed_json   JSONB NOT NULL DEFAULT '{}'::jsonb,          -- optional: whole parsed payload for debugging
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_statements_user_created ON app.statements(user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_statements_portfolio_status ON app.statements(portfolio_id, status);

CREATE TRIGGER tg_statements_updated_at
BEFORE UPDATE ON app.statements
FOR EACH ROW EXECUTE FUNCTION app.tg_set_updated_at();

CREATE TABLE IF NOT EXISTS app.statement_trades (
  id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  statement_id  BIGINT NOT NULL REFERENCES app.statements(id) ON DELETE CASCADE,

  instrument_id BIGINT REFERENCES app.instruments(id) ON DELETE SET NULL, -- mapping result
  raw_ticker    TEXT,
  name          TEXT,

  trade_date    DATE,
  settlement_date DATE,
  side          TEXT CHECK (side IN ('BUY','SELL')),
  quantity      NUMERIC(20, 6),
  price         NUMERIC(20, 8),
  currency      CHAR(3),
  fee           NUMERIC(20, 6),
  tax           NUMERIC(20, 6),
  net_amount    NUMERIC(20, 6),

  row_hash      CHAR(64) NOT NULL,
  errors_json   JSONB NOT NULL DEFAULT '[]'::jsonb,
  warnings_json JSONB NOT NULL DEFAULT '[]'::jsonb,

  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (statement_id, row_hash)
);

CREATE INDEX IF NOT EXISTS idx_statement_trades_statement ON app.statement_trades(statement_id);
CREATE INDEX IF NOT EXISTS idx_statement_trades_instrument ON app.statement_trades(instrument_id);

CREATE TABLE IF NOT EXISTS app.statement_holdings (
  id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  statement_id   BIGINT NOT NULL REFERENCES app.statements(id) ON DELETE CASCADE,
  instrument_id  BIGINT REFERENCES app.instruments(id) ON DELETE SET NULL,
  raw_ticker     TEXT,
  name           TEXT,

  depository_qty NUMERIC(20, 6),
  margin_qty     NUMERIC(20, 6),
  short_qty      NUMERIC(20, 6),
  ref_price      NUMERIC(20, 8),
  market_value   NUMERIC(20, 6),

  created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_statement_holdings_statement ON app.statement_holdings(statement_id);

CREATE TABLE IF NOT EXISTS app.statement_dividends (
  id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  statement_id  BIGINT NOT NULL REFERENCES app.statements(id) ON DELETE CASCADE,
  instrument_id BIGINT REFERENCES app.instruments(id) ON DELETE SET NULL,
  ex_date       DATE NOT NULL,
  currency      CHAR(3) NOT NULL DEFAULT 'TWD',
  amount        NUMERIC(20, 6) NOT NULL,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_statement_dividends_statement ON app.statement_dividends(statement_id);

-- OCR jobs tracking (optional if you only use Redis; DB makes audit/debug easier)
CREATE TABLE IF NOT EXISTS app.ocr_jobs (
  id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  user_id       BIGINT NOT NULL REFERENCES app.users(id) ON DELETE CASCADE,
  file_id       BIGINT NOT NULL REFERENCES app.files(id) ON DELETE CASCADE,
  statement_id  BIGINT REFERENCES app.statements(id) ON DELETE SET NULL,
  status        TEXT NOT NULL DEFAULT 'QUEUED' CHECK (status IN ('QUEUED','RUNNING','FAILED','DONE')),
  progress      INT NOT NULL DEFAULT 0 CHECK (progress >= 0 AND progress <= 100),
  error_message TEXT,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_ocr_jobs_user_created ON app.ocr_jobs(user_id, created_at DESC);

CREATE TRIGGER tg_ocr_jobs_updated_at
BEFORE UPDATE ON app.ocr_jobs
FOR EACH ROW EXECUTE FUNCTION app.tg_set_updated_at();

-- =========================================
-- 8) AI reports (optional)
-- =========================================

CREATE TABLE IF NOT EXISTS app.ai_reports (
  id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  user_id       BIGINT NOT NULL REFERENCES app.users(id) ON DELETE CASCADE,
  instrument_id BIGINT REFERENCES app.instruments(id) ON DELETE SET NULL,
  input_summary JSONB NOT NULL DEFAULT '{}'::jsonb,
  output_text   TEXT NOT NULL,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_ai_reports_user_created ON app.ai_reports(user_id, created_at DESC);

-- =========================================
-- 9) RAG / Vector store (pgvector)
-- =========================================

-- Documents: snapshots/notes/reports etc.
CREATE TABLE IF NOT EXISTS vector.rag_documents (
  id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  user_id       BIGINT REFERENCES app.users(id) ON DELETE CASCADE,
  source_type   TEXT NOT NULL DEFAULT 'NOTE',               -- NOTE/REPORT/FILE/WEB...
  source_id     TEXT,                                       -- optional external id
  title         TEXT,
  meta          JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_rag_documents_user ON vector.rag_documents(user_id);

-- Chunks with embeddings
-- IMPORTANT: embedding dimension default 1536; adjust if you use a different model.
CREATE TABLE IF NOT EXISTS vector.rag_chunks (
  id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  document_id   BIGINT NOT NULL REFERENCES vector.rag_documents(id) ON DELETE CASCADE,
  user_id       BIGINT REFERENCES app.users(id) ON DELETE CASCADE, -- 冗餘 user_id，提升查詢效率
  chunk_index   INT NOT NULL,
  content       TEXT NOT NULL,
  embedding     vector(1536) NOT NULL,
  meta          JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (document_id, chunk_index)
);

-- Vector index (choose ONE; hnsw is great if your pgvector supports it)
-- CREATE INDEX IF NOT EXISTS idx_rag_chunks_embedding
--   ON vector.rag_chunks USING hnsw (embedding vector_cosine_ops);

-- Fallback ivfflat index (requires ANALYZE after load; tune lists)
CREATE INDEX IF NOT EXISTS idx_rag_chunks_embedding_ivfflat
  ON vector.rag_chunks USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);

CREATE INDEX IF NOT EXISTS idx_rag_chunks_document ON vector.rag_chunks(document_id);
CREATE INDEX IF NOT EXISTS idx_rag_chunks_user ON vector.rag_chunks(user_id);

-- =========================================
-- 10) COMMENTS（中文註解，DBeaver ER 圖可見）
-- =========================================

-- --- app.users ---
COMMENT ON TABLE app.users IS '使用者主表';
COMMENT ON COLUMN app.users.id IS '主鍵';
COMMENT ON COLUMN app.users.email IS '電子郵件（唯一）';
COMMENT ON COLUMN app.users.google_sub IS 'Google OAuth Subject ID（唯一）';
COMMENT ON COLUMN app.users.display_name IS '顯示名稱';
COMMENT ON COLUMN app.users.picture_url IS 'Google 頭像 URL';
COMMENT ON COLUMN app.users.status IS '狀態：ACTIVE/SUSPENDED/PENDING';
COMMENT ON COLUMN app.users.last_login_at IS '最後登入時間';
COMMENT ON COLUMN app.users.created_at IS '建立時間';
COMMENT ON COLUMN app.users.updated_at IS '更新時間';

-- --- app.user_settings ---
COMMENT ON TABLE app.user_settings IS '使用者偏好設定';
COMMENT ON COLUMN app.user_settings.user_id IS '使用者 ID（主鍵）';
COMMENT ON COLUMN app.user_settings.base_currency IS '基準幣別（預設 TWD）';
COMMENT ON COLUMN app.user_settings.display_timezone IS '顯示時區（預設 Asia/Taipei）';

-- --- app.markets ---
COMMENT ON TABLE app.markets IS '市場主檔（TW/US）';
COMMENT ON COLUMN app.markets.id IS '主鍵';
COMMENT ON COLUMN app.markets.code IS '市場代碼：TW/US';
COMMENT ON COLUMN app.markets.name IS '市場名稱';
COMMENT ON COLUMN app.markets.timezone IS '市場時區';
COMMENT ON COLUMN app.markets.default_currency IS '預設幣別';

-- --- app.exchanges ---
COMMENT ON TABLE app.exchanges IS '交易所主檔';
COMMENT ON COLUMN app.exchanges.id IS '主鍵';
COMMENT ON COLUMN app.exchanges.market_id IS '所屬市場 ID';
COMMENT ON COLUMN app.exchanges.mic IS '交易所代碼（MIC）';
COMMENT ON COLUMN app.exchanges.code IS '簡稱';
COMMENT ON COLUMN app.exchanges.name IS '交易所名稱';

-- --- app.instruments ---
COMMENT ON TABLE app.instruments IS '商品主檔（股票/ETF）';
COMMENT ON COLUMN app.instruments.id IS '主鍵';
COMMENT ON COLUMN app.instruments.market_id IS '市場 ID';
COMMENT ON COLUMN app.instruments.exchange_id IS '交易所 ID';
COMMENT ON COLUMN app.instruments.ticker IS '股票代號（如 AAPL/2330）';
COMMENT ON COLUMN app.instruments.name_zh IS '中文名稱';
COMMENT ON COLUMN app.instruments.name_en IS '英文名稱';
COMMENT ON COLUMN app.instruments.currency IS '交易幣別';
COMMENT ON COLUMN app.instruments.status IS '狀態：ACTIVE/DELISTED/SUSPENDED';
COMMENT ON COLUMN app.instruments.symbol_key IS '唯一識別碼（如 US:XNAS:AAPL）';

-- --- app.instrument_aliases ---
COMMENT ON TABLE app.instrument_aliases IS '商品別名對照表';
COMMENT ON COLUMN app.instrument_aliases.id IS '主鍵';
COMMENT ON COLUMN app.instrument_aliases.instrument_id IS '商品 ID';
COMMENT ON COLUMN app.instrument_aliases.source IS '別名來源：OCR/MANUAL/DATA_VENDOR';
COMMENT ON COLUMN app.instrument_aliases.alias_ticker IS '別名（如 BRK-B 對應 BRK.B）';

-- --- app.prices ---
COMMENT ON TABLE app.prices IS '行情資料（K 線）';
COMMENT ON COLUMN app.prices.instrument_id IS '商品 ID';
COMMENT ON COLUMN app.prices.time_interval IS '時間週期：1m/5m/15m/1h/1d/1w/1mo';
COMMENT ON COLUMN app.prices.ts_utc IS '時間戳（UTC）';
COMMENT ON COLUMN app.prices.open IS '開盤價';
COMMENT ON COLUMN app.prices.high IS '最高價';
COMMENT ON COLUMN app.prices.low IS '最低價';
COMMENT ON COLUMN app.prices.close IS '收盤價';
COMMENT ON COLUMN app.prices.volume IS '成交量';
COMMENT ON COLUMN app.prices.source IS '資料來源（yahoo/fugle）';

-- --- app.fx_rates ---
COMMENT ON TABLE app.fx_rates IS '匯率資料';
COMMENT ON COLUMN app.fx_rates.base_currency IS '基準幣別';
COMMENT ON COLUMN app.fx_rates.quote_currency IS '報價幣別';
COMMENT ON COLUMN app.fx_rates.ts_utc IS '時間戳（UTC）';
COMMENT ON COLUMN app.fx_rates.rate IS '匯率';

-- --- app.corporate_actions ---
COMMENT ON TABLE app.corporate_actions IS '公司行為（拆股/股利）';
COMMENT ON COLUMN app.corporate_actions.id IS '主鍵';
COMMENT ON COLUMN app.corporate_actions.instrument_id IS '商品 ID';
COMMENT ON COLUMN app.corporate_actions.action_type IS '類型：SPLIT/DIVIDEND/RENAME/MERGER/SPINOFF';
COMMENT ON COLUMN app.corporate_actions.ex_date IS '除權息日';
COMMENT ON COLUMN app.corporate_actions.effective_date IS '生效日';
COMMENT ON COLUMN app.corporate_actions.payload IS '詳細資料（JSONB）';

-- --- app.portfolios ---
COMMENT ON TABLE app.portfolios IS '投資組合';
COMMENT ON COLUMN app.portfolios.id IS '主鍵';
COMMENT ON COLUMN app.portfolios.user_id IS '使用者 ID';
COMMENT ON COLUMN app.portfolios.name IS '組合名稱';
COMMENT ON COLUMN app.portfolios.base_currency IS '基準幣別';

-- --- app.accounts ---
COMMENT ON TABLE app.accounts IS '券商帳戶';
COMMENT ON COLUMN app.accounts.id IS '主鍵';
COMMENT ON COLUMN app.accounts.portfolio_id IS '投資組合 ID';
COMMENT ON COLUMN app.accounts.name IS '帳戶名稱';
COMMENT ON COLUMN app.accounts.broker IS '券商名稱';
COMMENT ON COLUMN app.accounts.currency IS '帳戶幣別';

-- --- app.stock_trades ---
COMMENT ON TABLE app.stock_trades IS '交易紀錄';
COMMENT ON COLUMN app.stock_trades.id IS '主鍵';
COMMENT ON COLUMN app.stock_trades.user_id IS '使用者 ID';
COMMENT ON COLUMN app.stock_trades.portfolio_id IS '投資組合 ID';
COMMENT ON COLUMN app.stock_trades.account_id IS '券商帳戶 ID';
COMMENT ON COLUMN app.stock_trades.instrument_id IS '商品 ID';
COMMENT ON COLUMN app.stock_trades.trade_date IS '交易日期（西元）';
COMMENT ON COLUMN app.stock_trades.settlement_date IS '交割日期';
COMMENT ON COLUMN app.stock_trades.side IS '買賣方向：BUY/SELL';
COMMENT ON COLUMN app.stock_trades.quantity IS '數量';
COMMENT ON COLUMN app.stock_trades.price IS '單價';
COMMENT ON COLUMN app.stock_trades.currency IS '幣別';
COMMENT ON COLUMN app.stock_trades.gross_amount IS '交易總額';
COMMENT ON COLUMN app.stock_trades.fee IS '手續費';
COMMENT ON COLUMN app.stock_trades.tax IS '稅金';
COMMENT ON COLUMN app.stock_trades.net_amount IS '淨額（買為負、賣為正）';
COMMENT ON COLUMN app.stock_trades.source IS '來源：OCR/MANUAL/API/MIGRATION';
COMMENT ON COLUMN app.stock_trades.source_ref_id IS '來源參照 ID';
COMMENT ON COLUMN app.stock_trades.row_hash IS 'SHA256 去重雜湊';

-- --- app.user_positions ---
COMMENT ON TABLE app.user_positions IS '持倉快取表';
COMMENT ON COLUMN app.user_positions.portfolio_id IS '投資組合 ID';
COMMENT ON COLUMN app.user_positions.instrument_id IS '商品 ID';
COMMENT ON COLUMN app.user_positions.total_quantity IS '總持股數量';
COMMENT ON COLUMN app.user_positions.avg_cost_native IS '加權平均成本';
COMMENT ON COLUMN app.user_positions.currency IS '成本幣別';

-- --- app.cash_ledger ---
COMMENT ON TABLE app.cash_ledger IS '現金帳本';
COMMENT ON COLUMN app.cash_ledger.id IS '主鍵';
COMMENT ON COLUMN app.cash_ledger.user_id IS '使用者 ID';
COMMENT ON COLUMN app.cash_ledger.portfolio_id IS '投資組合 ID';
COMMENT ON COLUMN app.cash_ledger.account_id IS '券商帳戶 ID';
COMMENT ON COLUMN app.cash_ledger.ts_utc IS '時間戳（UTC）';
COMMENT ON COLUMN app.cash_ledger.currency IS '幣別';
COMMENT ON COLUMN app.cash_ledger.amount IS '金額（正為入、負為出）';
COMMENT ON COLUMN app.cash_ledger.entry_type IS '類型：DEPOSIT/WITHDRAW/TRADE_NET/FEE/TAX/DIVIDEND/INTEREST/ADJUSTMENT';
COMMENT ON COLUMN app.cash_ledger.ref_trade_id IS '關聯交易 ID';
COMMENT ON COLUMN app.cash_ledger.meta IS '其他資訊（JSONB）';

-- --- app.portfolio_valuations ---
COMMENT ON TABLE app.portfolio_valuations IS '投資組合估值快照';
COMMENT ON COLUMN app.portfolio_valuations.portfolio_id IS '投資組合 ID';
COMMENT ON COLUMN app.portfolio_valuations.as_of_date IS '估值日期';
COMMENT ON COLUMN app.portfolio_valuations.base_currency IS '基準幣別';
COMMENT ON COLUMN app.portfolio_valuations.total_value IS '總價值';
COMMENT ON COLUMN app.portfolio_valuations.cash_value IS '現金價值';
COMMENT ON COLUMN app.portfolio_valuations.positions_value IS '持倉價值';

-- --- app.files ---
COMMENT ON TABLE app.files IS '檔案上傳紀錄';
COMMENT ON COLUMN app.files.id IS '主鍵';
COMMENT ON COLUMN app.files.user_id IS '使用者 ID';
COMMENT ON COLUMN app.files.provider IS '儲存供應商：local/s3/minio';
COMMENT ON COLUMN app.files.bucket IS '儲存桶';
COMMENT ON COLUMN app.files.object_key IS '物件鍵';
COMMENT ON COLUMN app.files.sha256 IS '檔案雜湊（去重）';
COMMENT ON COLUMN app.files.size_bytes IS '檔案大小（bytes）';
COMMENT ON COLUMN app.files.content_type IS 'MIME 類型';

-- --- app.statements ---
COMMENT ON TABLE app.statements IS '匯入批次（OCR/手動）';
COMMENT ON COLUMN app.statements.id IS '主鍵';
COMMENT ON COLUMN app.statements.user_id IS '使用者 ID';
COMMENT ON COLUMN app.statements.portfolio_id IS '投資組合 ID';
COMMENT ON COLUMN app.statements.source IS '來源：OCR/MANUAL';
COMMENT ON COLUMN app.statements.file_id IS '檔案 ID（OCR 才有）';
COMMENT ON COLUMN app.statements.status IS '狀態：DRAFT/REVIEWED/CONFIRMED/FAILED';
COMMENT ON COLUMN app.statements.period_start IS '對帳單起始日';
COMMENT ON COLUMN app.statements.period_end IS '對帳單結束日';
COMMENT ON COLUMN app.statements.raw_text IS 'OCR 原始文字';
COMMENT ON COLUMN app.statements.parsed_json IS '解析結果（JSONB）';

-- --- app.statement_trades ---
COMMENT ON TABLE app.statement_trades IS '匯入交易草稿';
COMMENT ON COLUMN app.statement_trades.id IS '主鍵';
COMMENT ON COLUMN app.statement_trades.statement_id IS '所屬匯入批次 ID';
COMMENT ON COLUMN app.statement_trades.instrument_id IS '對應商品 ID（解析後）';
COMMENT ON COLUMN app.statement_trades.raw_ticker IS '原始股票代號';
COMMENT ON COLUMN app.statement_trades.name IS '股票名稱';
COMMENT ON COLUMN app.statement_trades.trade_date IS '交易日期';
COMMENT ON COLUMN app.statement_trades.settlement_date IS '交割日期';
COMMENT ON COLUMN app.statement_trades.side IS '買賣方向：BUY/SELL';
COMMENT ON COLUMN app.statement_trades.quantity IS '數量';
COMMENT ON COLUMN app.statement_trades.price IS '單價';
COMMENT ON COLUMN app.statement_trades.currency IS '幣別';
COMMENT ON COLUMN app.statement_trades.fee IS '手續費';
COMMENT ON COLUMN app.statement_trades.tax IS '稅金';
COMMENT ON COLUMN app.statement_trades.net_amount IS '淨額';
COMMENT ON COLUMN app.statement_trades.row_hash IS '列雜湊（去重）';
COMMENT ON COLUMN app.statement_trades.errors_json IS '錯誤列表（JSONB）';
COMMENT ON COLUMN app.statement_trades.warnings_json IS '警告列表（JSONB）';

-- --- app.statement_holdings ---
COMMENT ON TABLE app.statement_holdings IS '匯入庫存（對帳用）';
COMMENT ON COLUMN app.statement_holdings.id IS '主鍵';
COMMENT ON COLUMN app.statement_holdings.statement_id IS '所屬匯入批次 ID';
COMMENT ON COLUMN app.statement_holdings.instrument_id IS '對應商品 ID';
COMMENT ON COLUMN app.statement_holdings.raw_ticker IS '原始股票代號';
COMMENT ON COLUMN app.statement_holdings.name IS '股票名稱';
COMMENT ON COLUMN app.statement_holdings.depository_qty IS '集保庫存';
COMMENT ON COLUMN app.statement_holdings.margin_qty IS '融資庫存';
COMMENT ON COLUMN app.statement_holdings.short_qty IS '融券庫存';
COMMENT ON COLUMN app.statement_holdings.ref_price IS '參考價';
COMMENT ON COLUMN app.statement_holdings.market_value IS '市值';

-- --- app.statement_dividends ---
COMMENT ON TABLE app.statement_dividends IS '匯入股利紀錄';
COMMENT ON COLUMN app.statement_dividends.id IS '主鍵';
COMMENT ON COLUMN app.statement_dividends.statement_id IS '所屬匯入批次 ID';
COMMENT ON COLUMN app.statement_dividends.instrument_id IS '商品 ID';
COMMENT ON COLUMN app.statement_dividends.ex_date IS '除息日';
COMMENT ON COLUMN app.statement_dividends.currency IS '幣別';
COMMENT ON COLUMN app.statement_dividends.amount IS '股利金額';

-- --- app.ocr_jobs ---
COMMENT ON TABLE app.ocr_jobs IS 'OCR 任務';
COMMENT ON COLUMN app.ocr_jobs.id IS '主鍵';
COMMENT ON COLUMN app.ocr_jobs.user_id IS '使用者 ID';
COMMENT ON COLUMN app.ocr_jobs.file_id IS '檔案 ID';
COMMENT ON COLUMN app.ocr_jobs.statement_id IS '對應匯入批次 ID';
COMMENT ON COLUMN app.ocr_jobs.status IS '狀態：QUEUED/RUNNING/FAILED/DONE';
COMMENT ON COLUMN app.ocr_jobs.progress IS '進度（0-100）';
COMMENT ON COLUMN app.ocr_jobs.error_message IS '錯誤訊息';

-- --- app.ai_reports ---
COMMENT ON TABLE app.ai_reports IS 'AI 分析報告';
COMMENT ON COLUMN app.ai_reports.id IS '主鍵';
COMMENT ON COLUMN app.ai_reports.user_id IS '使用者 ID';
COMMENT ON COLUMN app.ai_reports.instrument_id IS '相關商品 ID';
COMMENT ON COLUMN app.ai_reports.input_summary IS '輸入摘要（JSONB）';
COMMENT ON COLUMN app.ai_reports.output_text IS 'AI 生成內容';

-- --- vector.rag_documents ---
COMMENT ON TABLE vector.rag_documents IS 'RAG 文件主表';
COMMENT ON COLUMN vector.rag_documents.id IS '主鍵';
COMMENT ON COLUMN vector.rag_documents.user_id IS '使用者 ID（資料隔離）';
COMMENT ON COLUMN vector.rag_documents.source_type IS '來源類型：NOTE/REPORT/FILE/WEB';
COMMENT ON COLUMN vector.rag_documents.source_id IS '外部來源 ID';
COMMENT ON COLUMN vector.rag_documents.title IS '文件標題';
COMMENT ON COLUMN vector.rag_documents.meta IS '元資料（JSONB）';

-- --- vector.rag_chunks ---
COMMENT ON TABLE vector.rag_chunks IS 'RAG 文字分塊（含向量）';
COMMENT ON COLUMN vector.rag_chunks.id IS '主鍵';
COMMENT ON COLUMN vector.rag_chunks.document_id IS '所屬文件 ID';
COMMENT ON COLUMN vector.rag_chunks.user_id IS '使用者 ID（冗餘，提升查詢效率）';
COMMENT ON COLUMN vector.rag_chunks.chunk_index IS '區塊索引';
COMMENT ON COLUMN vector.rag_chunks.content IS '區塊內容';
COMMENT ON COLUMN vector.rag_chunks.embedding IS '向量嵌入（1536 維）';
COMMENT ON COLUMN vector.rag_chunks.meta IS '元資料（JSONB）';

COMMIT;

-- =========================================
-- End of DDL
-- =========================================
