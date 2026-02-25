# Backend Smoke Test Baseline

> Last updated: 2026-02-16

## Latest Update (2026-02-16)
- Security low-coupling update:
  - `app-auth` only enables `oauth2Login` when `ClientRegistrationRepository` exists.
  - This prevents hard coupling to OAuth2 client beans in non-OAuth2 test/runtime contexts.
- Added regression test:
  - `SecurityConfigOptionalOauth2IntegrationTest` verifies authenticated request path works without OAuth2 client registration beans.
- Test runtime stabilization:
  - Parent `pom.xml` surefire now sets:
    - `-XX:+EnableDynamicAgentLoading`
    - `sentry.enabled=false`
    - `sentry.dsn=`
    - `sentry.logs.enabled=false`
  - Purpose: keep local runs stable and avoid Sentry initialization noise during tests.

## Goal
- Provide a fast and repeatable backend API smoke baseline.
- Validate core entry points before and after backend fixes.

## Coverage
- `auth`: `googleLogin`
- `files`: `getFileUrl` (local provider)
- `ocr`: `retry`
- `rag`: `query`
- `ai`: `listReports`
- `stocks`: `getMarkets`
- `portfolio`: `listValuations`
- `security/csrf`: enabled mode reject/allow paths
- `security/optional-oauth2`: no OAuth2 client registration bean startup/request path

Smoke test file:
- `apps/backend/invest-assistant-backend/modules/app-bootstrap/src/test/java/tw/bk/appbootstrap/smoke/BackendApiSmokeBaselineTest.java`
- `apps/backend/invest-assistant-backend/modules/app-auth/src/test/java/tw/bk/appauth/config/SecurityConfigCsrfIntegrationTest.java`
- `apps/backend/invest-assistant-backend/modules/app-auth/src/test/java/tw/bk/appauth/config/SecurityConfigOptionalOauth2IntegrationTest.java`
- `apps/backend/invest-assistant-backend/modules/app-api/src/test/java/tw/bk/appapi/auth/AuthControllerTest.java`

## How To Run
Run from `apps/backend/invest-assistant-backend`:

```bash
.\\mvnw.cmd -pl modules/app-bootstrap -am "-Dmaven.test.skip=true" install
.\\mvnw.cmd -pl modules/app-bootstrap "-Denforcer.skip=true" "-Dtest=BackendApiSmokeBaselineTest" test
.\\mvnw.cmd -pl modules/app-auth,modules/app-api -am "-Dtest=SecurityConfigCsrfIntegrationTest,AuthControllerTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
.\\mvnw.cmd clean test
```

Optional focused auth security run:

```bash
.\\mvnw.cmd -pl modules/app-auth -am test
```

## Expected Baseline Result
- Full backend `clean test` baseline:
  - `BUILD SUCCESS`
  - `suites=25`
  - `tests=69`
  - `failures=0`
  - `errors=0`
  - `skipped=0`

## When To Use
- Before and after fixing `auth/files/ocr/rag/ai/stocks/portfolio`.
- As a quick local regression guard before merge.
- As a temporary first-line check before full integration coverage is completed in CI.

## CSRF Manual Smoke (Enabled)
Prerequisite:
- set `APP_SECURITY_CSRF_ENABLED=true` (or equivalent `app.security.csrf.enabled=true`)

1. Bootstrap token:
- `GET /api/auth/csrf`
- expect `200`, `data.enabled=true`, `data.headerName`, `data.token`, and `Set-Cookie: XSRF-TOKEN=...`

2. Reject path (missing CSRF):
- call any state-changing endpoint (`POST/PATCH/DELETE`) without CSRF header
- expect `403` with `AUTH_FORBIDDEN`

3. Allow path (valid CSRF):
- call same endpoint with:
  - cookie: `XSRF-TOKEN=<token>`
  - header: `X-XSRF-TOKEN: <token>`
- plus valid auth context (access cookie or bearer)
- expect request is no longer blocked by CSRF (`2xx` or domain-specific non-CSRF error)

4. SSE POST:
- `POST /api/ai/analysis/stream` or `POST /api/ai/conversations/{id}/messages`
- without CSRF token -> expect `403`
- with CSRF token -> expect stream can start (`200`, `text/event-stream`)

5. Swagger:
- open `/api/swagger-ui/index.html`
- call `GET /api/auth/csrf` first
- then execute `POST/PATCH/DELETE`
- expect no CSRF 403

6. Dev fallback:
- set `APP_SECURITY_CSRF_ENABLED=false`
- verify local flow still works without CSRF header
