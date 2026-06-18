-- Conversation message status should remain TEXT, but only known assistant statuses are allowed.
ALTER TABLE app.conversation_messages
    DROP CONSTRAINT IF EXISTS chk_conversation_messages_status;

ALTER TABLE app.conversation_messages
    ADD CONSTRAINT chk_conversation_messages_status
        CHECK (status IS NULL OR status IN ('PENDING', 'COMPLETED', 'FAILED'));

-- row_hash is a similarity fingerprint, not a true transaction identity.
ALTER TABLE app.stock_trades
    DROP CONSTRAINT IF EXISTS stock_trades_row_hash_key;

CREATE INDEX IF NOT EXISTS idx_stock_trades_row_hash
    ON app.stock_trades(row_hash);

-- Idempotency for imports should be based on the source row identity.
CREATE UNIQUE INDEX IF NOT EXISTS uq_stock_trades_source_ref
    ON app.stock_trades(user_id, portfolio_id, source, source_ref_id)
    WHERE source_ref_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_stock_trades_user_portfolio_date
    ON app.stock_trades(user_id, portfolio_id, trade_date DESC, id DESC);

-- statement_trades row_hash is used for duplicate warning, not hard blocking.
ALTER TABLE app.statement_trades
    DROP CONSTRAINT IF EXISTS statement_trades_statement_id_row_hash_key;

CREATE INDEX IF NOT EXISTS idx_statement_trades_statement_row_hash
    ON app.statement_trades(statement_id, row_hash);

CREATE INDEX IF NOT EXISTS idx_conversations_active_user_updated
    ON app.conversations(user_id, updated_at DESC)
    WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_users_email_lower
    ON app.users (lower(email));

-- V1 already creates idx_rag_chunks_embedding_ivfflat. V15 may add this duplicate index.
DROP INDEX IF EXISTS vector.idx_rag_chunks_embedding;
