-- V16__add_admin_local_login.sql
--
-- Add:
-- - Local password support for admin users (password_hash)
-- - Simple RBAC role column (USER / ADMIN)
-- - Allow google_sub to be nullable so a user can be local-only
-- - Admin login audit table + stronger integrity constraints

-- 1) Users: allow google_sub nullable (local-only users)
ALTER TABLE app.users
    ALTER COLUMN google_sub DROP NOT NULL;

-- 2) Users: password hash (algorithm-agnostic) - nullable
ALTER TABLE app.users
    ADD COLUMN IF NOT EXISTS password_hash TEXT;

-- 3) Users: role (simple RBAC)
ALTER TABLE app.users
    ADD COLUMN IF NOT EXISTS role TEXT;

UPDATE app.users
SET role = 'USER'
WHERE role IS NULL;

ALTER TABLE app.users
    ALTER COLUMN role SET DEFAULT 'USER';

ALTER TABLE app.users
    ALTER COLUMN role SET NOT NULL;

ALTER TABLE app.users
    DROP CONSTRAINT IF EXISTS chk_users_role;

ALTER TABLE app.users
    ADD CONSTRAINT chk_users_role
        CHECK (role IN ('USER', 'ADMIN'));

-- Prevent zombie users that cannot login by any method.
ALTER TABLE app.users
    DROP CONSTRAINT IF EXISTS chk_users_login_identity;

ALTER TABLE app.users
    ADD CONSTRAINT chk_users_login_identity
        CHECK (google_sub IS NOT NULL OR password_hash IS NOT NULL);

-- For current design: ADMIN must support local password login.
ALTER TABLE app.users
    DROP CONSTRAINT IF EXISTS chk_users_admin_password;

ALTER TABLE app.users
    ADD CONSTRAINT chk_users_admin_password
        CHECK (role <> 'ADMIN' OR password_hash IS NOT NULL);

-- 4) Admin login audits (for admin password login)
CREATE TABLE IF NOT EXISTS app.admin_login_audits (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email       TEXT NOT NULL,
    user_id     BIGINT,
    success     BOOLEAN NOT NULL,
    ip          INET,
    user_agent  TEXT,
    reason      TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_admin_login_audits_user
        FOREIGN KEY (user_id) REFERENCES app.users (id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_admin_login_audits_created_at ON app.admin_login_audits (created_at DESC);
CREATE INDEX IF NOT EXISTS idx_admin_login_audits_email ON app.admin_login_audits (email);

-- 5) DB comments (Traditional Chinese)
COMMENT ON COLUMN app.users.google_sub IS 'Google OAuth Subject ID（可為空；本地帳號可不綁 Google）';
COMMENT ON COLUMN app.users.password_hash IS '本地登入密碼雜湊（演算法中立，例如 Argon2id）';
COMMENT ON COLUMN app.users.role IS '使用者角色（USER/ADMIN）';
COMMENT ON CONSTRAINT chk_users_role ON app.users IS '角色必須為 USER 或 ADMIN';
COMMENT ON CONSTRAINT chk_users_login_identity ON app.users IS '至少要有一種登入識別（google_sub 或 password_hash）';
COMMENT ON CONSTRAINT chk_users_admin_password ON app.users IS 'ADMIN 必須具備本地密碼雜湊';

COMMENT ON TABLE app.admin_login_audits IS '管理員本地登入稽核紀錄';
COMMENT ON COLUMN app.admin_login_audits.email IS '登入嘗試使用的 Email';
COMMENT ON COLUMN app.admin_login_audits.user_id IS '對應使用者 ID（可為空，代表查無此帳號）';
COMMENT ON COLUMN app.admin_login_audits.success IS '登入是否成功';
COMMENT ON COLUMN app.admin_login_audits.ip IS '來源 IP（PostgreSQL inet）';
COMMENT ON COLUMN app.admin_login_audits.user_agent IS '請求的 User-Agent';
COMMENT ON COLUMN app.admin_login_audits.reason IS '失敗/告警原因代碼';
COMMENT ON COLUMN app.admin_login_audits.created_at IS '建立時間';
