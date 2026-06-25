# Grafana (metric alerting → Slack)

Grafana runs next to Prometheus and handles **metric-threshold alerts** (the gap
Sentry doesn't cover: cost/rate/latency). It uses **Grafana-managed alerting**, so
**no Alertmanager is needed**. Everything here is provisioned from files, so the
setup is reproducible and version-controlled.

```
Prometheus ──(query)── Grafana ──(threshold alert)── Slack #prod
                          └──────── dashboards (build in UI)
```

## What's provisioned
| File | Purpose |
|------|---------|
| `provisioning/datasources/prometheus.yaml` | Prometheus data source (`uid: prometheus`) |
| `provisioning/alerting/contact-points.yaml` | Slack contact point (URL from `SLACK_WEBHOOK_PROD` env) |
| `provisioning/alerting/policies.yaml` | Default notification policy → Slack |
| `provisioning/alerting/rules.yaml` | 5 alert rules (see below) |

### Alert rules (tune thresholds to your traffic)
| Rule | Fires when | `for` |
|------|-----------|-------|
| Service down | `up{job=~"invest-assistant-backend\|ai-worker"} == 0` | 2m |
| API 5xx ratio high | backend 5xx ratio > 5% | 10m |
| AI provider call failures | > 5 failed `ai_calls_total` in 1h | 5m |
| AI token usage spike | > 200k `ai_tokens_total` in 1h (cost proxy) | 5m |
| OCR fallback spike | > 10 `ocr_fallback_total` in 1h | 5m |

## Setup
1. Create a Slack **incoming webhook** for your `#prod` alerts channel; copy the URL.
2. In `.env.prod` set:
   ```
   GRAFANA_ADMIN_PASSWORD=<strong password>
   SLACK_WEBHOOK_PROD=<the Slack webhook URL>
   ```
3. Start the stack:
   ```
   docker compose -f compose.prod.yaml --env-file .env.prod up -d grafana
   ```
4. Open Grafana at `http://<host>:${GRAFANA_PORT:-3000}` (login: `admin` / your password).
   - Alerting → Alert rules: the 5 rules appear under **Infra Alerts**.
   - Alerting → Contact points: **slack** is configured.
   - Test: Contact points → slack → **Test** → you should get a Slack message.

## Notes
- **Security**: Grafana is an internal ops tool — keep port 3000 off the public
  Cloudflare hostname (it's not behind nginx). Access over LAN / SSH tunnel.
- **Dev routing**: Prometheus only scrapes the prod stack, so all alerts go to the
  prod channel. To add a `#dev` channel later, add a `slack-dev` contact point and
  a child route in `policies.yaml` matching an `environment = dev` label.
- **Dashboards**: not provisioned — build them in the UI, or drop providers +
  JSON into `provisioning/dashboards/`. The admin `AnalyticsPanel` already covers
  the product-facing view.
