/**
 * Sentry → Slack relay (Cloudflare Worker).
 *
 * Sentry's native Slack integration is a paid feature, but Slack incoming
 * webhooks are free. This Worker receives Sentry issue-alert webhooks
 * (sent by a Sentry Internal Integration), verifies the HMAC signature, and
 * posts a formatted message to the Slack channel that matches the event's
 * environment (prod vs dev).
 *
 * Secrets (set with `wrangler secret put <NAME>`):
 *   SENTRY_CLIENT_SECRET  Internal Integration client secret (signature check)
 *   SLACK_WEBHOOK_PROD    Slack incoming webhook URL for the prod channel
 *   SLACK_WEBHOOK_DEV     Slack incoming webhook URL for the dev channel (optional)
 */

export interface Env {
  SENTRY_CLIENT_SECRET: string;
  SLACK_WEBHOOK_PROD: string;
  SLACK_WEBHOOK_DEV?: string;
}

interface SentryEvent {
  title?: string;
  culprit?: string;
  level?: string;
  environment?: string;
  web_url?: string;
  issue_url?: string;
  project?: number | string;
  metadata?: { type?: string; value?: string };
}

interface SentryPayload {
  action?: string;
  data?: {
    event?: SentryEvent;
    triggered_rule?: string;
  };
}

const PROD_ENVIRONMENTS = new Set(['production', 'prod']);

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    if (request.method !== 'POST') {
      return new Response('Method Not Allowed', { status: 405 });
    }

    const body = await request.text();

    // The Worker URL is public, so verify Sentry's signature to reject spoofs.
    const signature = request.headers.get('sentry-hook-signature') ?? '';
    if (
      !env.SENTRY_CLIENT_SECRET
      || !(await verifySignature(env.SENTRY_CLIENT_SECRET, body, signature))
    ) {
      return new Response('Invalid signature', { status: 401 });
    }

    // Sentry pings 'installation' on setup and may send other resources;
    // only issue/event alerts produce a Slack message. Ack the rest with 200.
    const resource = request.headers.get('sentry-hook-resource') ?? '';
    if (resource !== 'event_alert' && resource !== 'issue') {
      return new Response('ignored', { status: 200 });
    }

    let payload: SentryPayload;
    try {
      payload = JSON.parse(body) as SentryPayload;
    } catch {
      return new Response('Bad JSON', { status: 400 });
    }

    const event = payload.data?.event;
    if (!event) {
      return new Response('No event', { status: 200 });
    }

    const webhookUrl = pickWebhookUrl(event.environment, env);
    if (!webhookUrl) {
      return new Response('No Slack webhook configured', { status: 200 });
    }

    const message = buildSlackMessage(event, payload.data?.triggered_rule);
    const slackResp = await fetch(webhookUrl, {
      method: 'POST',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify(message),
    });

    // Phase 3 seam: this is the natural place to also kick off the AI triage
    // agent — e.g. enqueue event.issue_url / event.web_url for root-cause
    // analysis. Left out intentionally until Phase 3.

    if (!slackResp.ok) {
      return new Response(`Slack error: ${slackResp.status}`, { status: 502 });
    }
    return new Response('ok', { status: 200 });
  },
};

/** Route prod environments to the prod channel; everything else to dev (falling back to prod). */
export function pickWebhookUrl(environment: string | undefined, env: Env): string | undefined {
  const normalized = (environment ?? '').trim().toLowerCase();
  if (PROD_ENVIRONMENTS.has(normalized)) {
    return env.SLACK_WEBHOOK_PROD;
  }
  return env.SLACK_WEBHOOK_DEV ?? env.SLACK_WEBHOOK_PROD;
}

/** Build a compact Slack Block Kit message from a Sentry event. */
export function buildSlackMessage(event: SentryEvent, rule?: string): unknown {
  const environment = event.environment ?? 'unknown';
  const level = (event.level ?? 'error').toLowerCase();
  const emoji = level === 'fatal' || level === 'error'
    ? '🔴'
    : level === 'warning'
      ? '🟠'
      : 'ℹ️';
  const title = event.title ?? event.metadata?.value ?? 'Sentry alert';
  const link = event.web_url ?? event.issue_url;
  const titleText = link ? `<${link}|${escapeMrkdwn(title)}>` : escapeMrkdwn(title);

  const context = [
    `env: *${escapeMrkdwn(environment)}*`,
    `level: ${escapeMrkdwn(level)}`,
    event.culprit ? `at \`${escapeMrkdwn(event.culprit)}\`` : null,
    rule ? `rule: ${escapeMrkdwn(rule)}` : null,
  ]
    .filter((part): part is string => Boolean(part))
    .join('  ·  ');

  return {
    blocks: [
      {
        type: 'header',
        text: { type: 'plain_text', text: `${emoji} [${environment}] Sentry`, emoji: true },
      },
      { type: 'section', text: { type: 'mrkdwn', text: `*${titleText}*` } },
      { type: 'context', elements: [{ type: 'mrkdwn', text: context }] },
    ],
  };
}

function escapeMrkdwn(text: string): string {
  return text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
}

async function verifySignature(secret: string, body: string, signature: string): Promise<boolean> {
  if (!signature) {
    return false;
  }
  const encoder = new TextEncoder();
  const key = await crypto.subtle.importKey(
    'raw',
    encoder.encode(secret),
    { name: 'HMAC', hash: 'SHA-256' },
    false,
    ['sign'],
  );
  const digest = await crypto.subtle.sign('HMAC', key, encoder.encode(body));
  const expected = [...new Uint8Array(digest)]
    .map((byte) => byte.toString(16).padStart(2, '0'))
    .join('');
  return timingSafeEqual(expected, signature);
}

function timingSafeEqual(a: string, b: string): boolean {
  if (a.length !== b.length) {
    return false;
  }
  let result = 0;
  for (let i = 0; i < a.length; i += 1) {
    result |= a.charCodeAt(i) ^ b.charCodeAt(i);
  }
  return result === 0;
}
