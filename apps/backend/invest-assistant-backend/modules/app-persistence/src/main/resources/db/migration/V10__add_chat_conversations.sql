-- Chat conversations & messages (v1)
CREATE TABLE IF NOT EXISTS app.conversations (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id     BIGINT NOT NULL REFERENCES app.users(id) ON DELETE CASCADE,
    title       TEXT,
    summary     TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_conversations_user_updated
    ON app.conversations(user_id, updated_at DESC);

CREATE TRIGGER tg_conversations_updated_at
    BEFORE UPDATE ON app.conversations
    FOR EACH ROW EXECUTE FUNCTION app.tg_set_updated_at();

CREATE TABLE IF NOT EXISTS app.conversation_messages (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    conversation_id    BIGINT NOT NULL REFERENCES app.conversations(id) ON DELETE CASCADE,
    role               TEXT NOT NULL CHECK (role IN ('system','user','assistant')),
    content            TEXT NOT NULL,
    status             TEXT,
    client_message_id  TEXT,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (conversation_id, client_message_id)
);

CREATE INDEX IF NOT EXISTS idx_conversation_messages_conv_id
    ON app.conversation_messages(conversation_id, id);
