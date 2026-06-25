-- Product analytics events for authenticated users.
-- No email, IP address, user-agent, query string, or business payload is stored.

CREATE TABLE IF NOT EXISTS app.analytics_events (
    event_id      UUID PRIMARY KEY,
    user_id       BIGINT NOT NULL REFERENCES app.users(id) ON DELETE CASCADE,
    session_id    UUID NOT NULL,
    event_type    TEXT NOT NULL,
    route         TEXT NOT NULL,
    occurred_at   TIMESTAMPTZ NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_analytics_events_type
        CHECK (event_type IN ('PAGE_VIEW')),
    CONSTRAINT chk_analytics_events_route
        CHECK (char_length(route) BETWEEN 1 AND 200 AND route LIKE '/%')
);

CREATE INDEX IF NOT EXISTS idx_analytics_events_occurred
    ON app.analytics_events (occurred_at DESC);
CREATE INDEX IF NOT EXISTS idx_analytics_events_user_occurred
    ON app.analytics_events (user_id, occurred_at DESC);
CREATE INDEX IF NOT EXISTS idx_analytics_events_type_route_occurred
    ON app.analytics_events (event_type, route, occurred_at DESC);
CREATE INDEX IF NOT EXISTS idx_analytics_events_session
    ON app.analytics_events (session_id);
CREATE INDEX IF NOT EXISTS idx_users_role_created
    ON app.users (role, created_at DESC);

COMMENT ON TABLE app.analytics_events IS '已登入使用者的隱私最小化產品分析事件';
COMMENT ON COLUMN app.analytics_events.event_id IS '前端產生的冪等事件 UUID';
COMMENT ON COLUMN app.analytics_events.user_id IS '由登入 JWT 決定的使用者 ID';
COMMENT ON COLUMN app.analytics_events.session_id IS '瀏覽器分頁工作階段 UUID';
COMMENT ON COLUMN app.analytics_events.event_type IS '事件類型，目前支援 PAGE_VIEW';
COMMENT ON COLUMN app.analytics_events.route IS 'SPA 路由，不包含 query string';
COMMENT ON COLUMN app.analytics_events.occurred_at IS '事件發生時間';
