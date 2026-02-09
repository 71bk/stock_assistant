# Auth 與安全設計

> 狀態：已實作 (2026-02-09)

## Cookie/JWT 策略

本系統採用 **Stateless JWT** 搭配 **HttpOnly Cookie** 的方式進行身分驗證，確保安全性並防禦 XSS 攻擊。

- **Access Token**:
  - 存放位置：`HttpOnly Cookie` (名稱: `access_token`)
  - 時效：短效期 (如 15 分鐘)
  - 內容：包含使用者 ID (sub)、角色 (roles) 等基本資訊
  - 驗證：由 `JwtAuthenticationFilter` 攔截請求並驗證簽章

- **Refresh Token**:
  - 存放位置：`HttpOnly Cookie` (名稱: `refresh_token`, path: `/api/auth/refresh`)
  - 時效：長效期 (如 7 天)
  - 用途：當 Access Token 過期時，用於換取新的 Access Token

## Refresh Rotation + Redis Session

為了增強安全性，我們實作了 **Refresh Token Rotation** 與 **Redis 黑名單** 機制。

- **Rotation**: 每次使用 Refresh Token 換發新的 Access Token 時，也會同時發放一個 **新的 Refresh Token**，舊的立即失效。
- **Redis Session 管理**:
  - 每次登入或換發 Token 時，會將 Refresh Token 的相關資訊 (jti, userId, deviceId) 存入 Redis。
  - **強制登出**：從 Redis 移除對應的 key，使該 Refresh Token 立即失效。
  - **防盜用偵測**：若偵測到已失效的 Refresh Token 被使用，可選擇將該使用者的所有 Token 設為黑名單 (實作細節視 RefreshTokenService 而定)。

## CORS/CSRF

- **CORS (Cross-Origin Resource Sharing)**:
  - 設定位置：`SecurityConfig.java` -> `corsConfigurationSource()`
  - 預設允許來源：`http://localhost:5173` (前端開發環境)
  - 允許方法：GET, POST, PUT, PATCH, DELETE, OPTIONS
  - 允許標頭：`*`
  - `AllowCredentials`: `true` (必須開啟才能傳送 Cookie)
  - **注意**：生產環境需透過 `APP_CORS_ALLOWED_ORIGINS` 環境變數設定正確的 Domain，嚴禁使用 `*`。

- **CSRF (Cross-Site Request Forgery)**:
  - 設定：`AbstractHttpConfigurer::disable`
  - 原因：因為我們使用 **HttpOnly Cookie** 且 API 主要是被 SPA (Single Page Application) 呼叫，配合 `SameSite` 屬性 (建議設為 Lax 或 Strict) 與 CORS 限制，已能有效防禦 CSRF。且 stateless 架構下，CSRF token 維護成本較高。

## 登出與撤銷

- **登出 API (`/api/auth/logout`)**:
  - 清除 Redis 中的 Refresh Token 紀錄。
  - 清除瀏覽器端的 Cookie (`access_token`, `refresh_token`)，設定 `Max-Age=0`。

## Refresh Endpoint Rate Limit

- `/api/auth/refresh` 已實作 IP 維度限流（`SimpleRateLimiter`）。
- 主要設定：
  - `app.auth.refresh.rate-limit`（預設 30）
  - `app.auth.refresh.rate-window`（預設 60s）

## Exception Handling (Security)

Spring Security 層級的異常由 `SecurityConfig` 中的 `exceptionHandling` 處理：

- **401 Unauthorized**: 未登入或 Token 無效 (由 `AuthenticationEntryPoint` 處理)，回傳標準 JSON 格式。
- **403 Forbidden**: 權限不足 (由 `AccessDeniedHandler` 處理)，回傳標準 JSON 格式。

## 安全檢查清單 (Self-Check)

- [x] HTTPS 強制開啟 (生產環境 Nginx 設定)
- [x] HttpOnly Cookie 防止 XSS 竊取 Token
- [x] Refresh Token Rotation 防止長期憑證遭竊
- [x] Redis 控管 Session 狀態，支援伺服器端強制登出
- [x] SQL Injection 防禦 (全面使用 JPA/Hibernate 參數化查詢)
