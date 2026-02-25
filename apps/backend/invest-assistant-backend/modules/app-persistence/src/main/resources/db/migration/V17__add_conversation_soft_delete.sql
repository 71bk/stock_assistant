-- Add soft-delete support for chat conversations
ALTER TABLE app.conversations
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;

ALTER TABLE app.conversations
    ADD COLUMN IF NOT EXISTS purge_after_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_conversations_purge_after
    ON app.conversations(purge_after_at)
    WHERE deleted_at IS NOT NULL;

COMMENT ON COLUMN app.conversations.deleted_at IS 'Soft-delete timestamp for user conversation delete';
COMMENT ON COLUMN app.conversations.purge_after_at IS 'Planned hard-delete timestamp for retention cleanup';
