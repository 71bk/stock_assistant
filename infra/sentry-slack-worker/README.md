# Sentry → Slack relay (Cloudflare Worker)

Sentry's native Slack integration is a paid feature, but Slack **incoming
webhooks** are free. This Worker bridges them: Sentry sends issue alerts to the
Worker (via an Internal Integration), the Worker verifies the signature and
posts a formatted message to the Slack channel for that environment
(`prod` → prod channel, everything else → dev channel).

When `GROQ_API_KEY` + the `TRIAGE_KV` namespace are configured, it also runs a
one-shot **AI triage** in the background and posts a second "🔍 AI Triage" message
(root-cause hypothesis + suggested fix from the stack trace), gated by per-issue
dedup and a daily budget. Omit `GROQ_API_KEY` to run as a plain relay.

## Flow

```
Sentry alert rule
  → (Internal Integration webhook, signed)
  → Cloudflare Worker  ── verify signature → pick channel by environment
       ├─ POST Slack incoming webhook → #prod / #dev          (🔴 alert)
       └─ (background, optional) Groq triage → POST Slack      (🔍 AI Triage)
```

## One-time setup

### 1. Slack — create incoming webhooks
For each channel (`#prod-alerts`, `#dev-alerts`): Slack → *Apps* → **Incoming
Webhooks** → *Add to Slack* → pick the channel → copy the webhook URL.

### 2. Deploy the Worker
```bash
cd infra/sentry-slack-worker
npm install
npm test            # optional: run unit tests
npx wrangler deploy # prints the Worker URL, e.g. https://sentry-slack-relay.<sub>.workers.dev
```

### 3. Set secrets (never commit these)
```bash
npx wrangler secret put SLACK_WEBHOOK_PROD     # paste the #prod webhook URL
npx wrangler secret put SLACK_WEBHOOK_DEV       # paste the #dev webhook URL
npx wrangler secret put SENTRY_CLIENT_SECRET    # from step 4 below
```

### 4. Sentry — create an Internal Integration
Sentry → *Settings* → *Developer Settings* → **Internal Integration** → *New*:
- **Webhook URL**: the Worker URL from step 2
- **Alert Rule Action**: enabled (so it appears as an alert action)
- Save, then copy the **Client Secret** → use it for `SENTRY_CLIENT_SECRET` (step 3).

### 5. Sentry — route alert rules to the Worker
Create two issue alert rules (or one per environment):

| Rule | Filter | Action |
|------|--------|--------|
| prod | `environment = production` | Send notification via this Internal Integration |
| dev  | `environment = dev`        | Send notification via this Internal Integration |

The Worker picks the Slack channel from the event's `environment`, so both rules
point at the same Worker.

## AI triage (optional, Phase 3)
Adds a "🔍 AI Triage" follow-up message via Groq. Skip this whole section to run
as a plain relay.

1. Create the KV namespace (once) and put its id in `wrangler.toml` under
   `[[kv_namespaces]]` (binding `TRIAGE_KV`):
   ```bash
   npx wrangler kv namespace create TRIAGE_KV
   ```
2. Set the Groq key:
   ```bash
   npx wrangler secret put GROQ_API_KEY
   ```
3. Optional tuning in `wrangler.toml` `[vars]`: `GROQ_MODEL` (default
   `openai/gpt-oss-120b`), `TRIAGE_DAILY_BUDGET` (default `50`).

Guardrails: one triage per Sentry issue (KV dedup), a per-day budget cap, only
in-app stack frames are sent, and emails/tokens are masked before the LLM call.
Runs in `ctx.waitUntil` so it never delays Sentry's webhook response.

> Note: Groq reasoning models (e.g. `gpt-oss`) spend tokens on hidden reasoning,
> so `max_tokens` is set generously (1500) to avoid empty completions.

## Local development
```bash
# Put secrets in .dev.vars (gitignored) for `wrangler dev`:
#   SENTRY_CLIENT_SECRET=...
#   SLACK_WEBHOOK_PROD=...
#   SLACK_WEBHOOK_DEV=...
npx wrangler dev
```

## Notes
- Signature verification (`Sentry-Hook-Signature`, HMAC-SHA256 of the raw body)
  rejects spoofed requests to the public Worker URL — `SENTRY_CLIENT_SECRET` is required.
- Non-alert webhooks (e.g. `installation`) are acknowledged with `200` and ignored.
- Free tier: Cloudflare Workers (100k req/day) and Slack incoming webhooks are
  both free, well within a personal app's alert volume.
