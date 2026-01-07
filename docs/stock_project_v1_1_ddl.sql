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
  status          TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','SUSPENDED','PENDING')),
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
CREATE INDEX IF NOT EXISTS idx_instruments_exchange_ticker ON app.instruments(exchange_id, ticker);

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

-- =========================================
-- 3) Market data cacheable tables (prices / fx)
-- =========================================

CREATE TABLE IF NOT EXISTS app.prices (
  instrument_id BIGINT NOT NULL REFERENCES app.instruments(id) ON DELETE CASCADE,
  interval      TEXT NOT NULL CHECK (interval IN ('1m','5m','15m','1h','1d','1w','1mo')),
  ts_utc        TIMESTAMPTZ NOT NULL,
  open          NUMERIC(20, 8) NOT NULL,
  high          NUMERIC(20, 8) NOT NULL,
  low           NUMERIC(20, 8) NOT NULL,
  close         NUMERIC(20, 8) NOT NULL,
  volume        NUMERIC(24, 6),
  PRIMARY KEY (instrument_id, interval, ts_utc)
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

COMMIT;

-- =========================================
-- End of DDL
-- =========================================
