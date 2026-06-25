/**
 * Sentry → Slack relay (Cloudflare Worker) with optional AI triage.
 *
 * Sentry's native Slack integration is paid, but Slack incoming webhooks are
 * free. This Worker receives Sentry issue-alert webhooks (Internal Integration),
 * verifies the HMAC signature, and posts a formatted message to the Slack channel
 * for the event's environment (prod → #prod, else → #dev).
 *
 * Phase 3 (optional): when GROQ_API_KEY + TRIAGE_KV are configured, it also runs
 * a one-shot AI triage in the background (ctx.waitUntil) and posts a second
 * "🔍 AI Triage" message — root-cause hypothesis + suggested fix from the stack
 * trace. Gated by per-issue dedup and a daily budget so a flapping error cannot
 * burn tokens.
 *
 * Secrets (wrangler secret put):
 *   SENTRY_CLIENT_SECRET  Internal Integration client secret (signature check)
 *   SLACK_WEBHOOK_PROD    Slack incoming webhook for the prod channel
 *   SLACK_WEBHOOK_DEV     Slack incoming webhook for the dev channel (optional)
 *   GROQ_API_KEY          enables AI triage; omit to run as a plain relay
 */

export interface Env {
  SENTRY_CLIENT_SECRET: string;
  SLACK_WEBHOOK_PROD: string;
  SLACK_WEBHOOK_DEV?: string;
  // AI triage (optional)
  GROQ_API_KEY?: string;
  GROQ_MODEL?: string;
  TRIAGE_DAILY_BUDGET?: string;
  TRIAGE_KV?: KVNamespace;
}

interface SentryFrame {
  filename?: string;
  module?: string;
  function?: string;
  lineno?: number;
  in_app?: boolean;
  context_line?: string;
}

interface SentryExceptionValue {
  type?: string;
  value?: string;
  stacktrace?: { frames?: SentryFrame[] };
}

interface SentryEvent {
  event_id?: string;
  issue_id?: string | number;
  title?: string;
  culprit?: string;
  level?: string;
  environment?: string;
  web_url?: string;
  issue_url?: string;
  metadata?: { type?: string; value?: string };
  exception?: { values?: SentryExceptionValue[] };
}

interface SentryPayload {
  action?: string;
  data?: {
    event?: SentryEvent;
    triggered_rule?: string;
  };
}

const PROD_ENVIRONMENTS = new Set(['production', 'prod']);

const TRIAGE_SYSTEM_PROMPT = [
  'You are a senior engineer doing fast incident triage from a Sentry error and its',
  'stack trace. Reply concisely (this goes into a Slack message) using exactly this',
  'structure with single-asterisk bold:',
  '*Root cause:* one or two sentences.',
  '*Likely location:* the most suspicious file:line / method from the stack.',
  '*Suggested fix:* a short, concrete direction.',
  '*Confidence:* low / medium / high.',
  'If the stack trace is insufficient, say what extra info is needed.',
  'Do not invent code or files you were not shown.',
].join(' ');

export default {
  async fetch(request: Request, env: Env, ctx: ExecutionContext): Promise<Response> {
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

    const slackResp = await fetch(webhookUrl, {
      method: 'POST',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify(buildSlackMessage(event, payload.data?.triggered_rule)),
    });

    // AI triage runs in the background so it never delays Sentry's 200 response.
    if (env.GROQ_API_KEY && env.TRIAGE_KV) {
      ctx.waitUntil(
        runTriage(event, webhookUrl, env).catch((err) =>
          console.log('AI triage failed:', err instanceof Error ? err.message : String(err))),
      );
    }

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

// ---------------------------------------------------------------------------
// AI triage
// ---------------------------------------------------------------------------

async function runTriage(event: SentryEvent, webhookUrl: string, env: Env): Promise<void> {
  const kv = env.TRIAGE_KV;
  if (!kv) {
    return;
  }

  const issueId = extractIssueId(event);
  if (issueId && (await kv.get(`triaged:${issueId}`))) {
    return; // already triaged this issue
  }

  const today = new Date().toISOString().slice(0, 10);
  const used = parseInt((await kv.get(`budget:${today}`)) ?? '0', 10);
  const limit = parseInt(env.TRIAGE_DAILY_BUDGET ?? '50', 10);
  if (Number.isFinite(used) && used >= limit) {
    return; // daily triage budget exhausted
  }

  const analysis = await callGroq(env, buildTriagePrompt(event));
  if (!analysis) {
    return;
  }

  await fetch(webhookUrl, {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify(buildTriageSlackMessage(analysis, event)),
  });

  if (issueId) {
    await kv.put(`triaged:${issueId}`, '1', { expirationTtl: 604800 });
  }
  await kv.put(`budget:${today}`, String((Number.isFinite(used) ? used : 0) + 1), {
    expirationTtl: 172800,
  });
}

export function extractIssueId(event: SentryEvent): string | null {
  if (event.issue_id !== undefined && event.issue_id !== null) {
    return String(event.issue_id);
  }
  const url = event.issue_url ?? event.web_url ?? '';
  const match = url.match(/issues\/(\d+)/);
  return match ? match[1] : null;
}

/** Format the exception + stack trace, preferring in-app frames. */
export function formatStack(event: SentryEvent, maxFrames = 12): string {
  const values = event.exception?.values ?? [];
  const lines: string[] = [];
  for (const value of values) {
    if (value.type || value.value) {
      lines.push(`${value.type ?? 'Error'}: ${value.value ?? ''}`.trim());
    }
    const frames = value.stacktrace?.frames ?? [];
    // Sentry frames are oldest-first; the throwing frame is last. Prefer app frames.
    const appFrames = frames.filter((frame) => frame.in_app);
    const chosen = (appFrames.length > 0 ? appFrames : frames).slice(-maxFrames);
    for (const frame of chosen) {
      const where = frame.module ?? frame.filename ?? '?';
      const fn = frame.function ?? '?';
      const ln = frame.lineno !== undefined ? `:${frame.lineno}` : '';
      lines.push(`  at ${where}.${fn}(${frame.filename ?? ''}${ln})`);
      if (frame.context_line) {
        lines.push(`      ${frame.context_line.trim()}`);
      }
    }
  }
  return lines.join('\n');
}

export function buildTriagePrompt(event: SentryEvent): string {
  const parts = [
    `Title: ${event.title ?? event.metadata?.value ?? 'unknown'}`,
    `Level: ${event.level ?? 'error'}`,
    `Environment: ${event.environment ?? 'unknown'}`,
    event.culprit ? `Culprit: ${event.culprit}` : null,
    '',
    'Stack trace:',
    formatStack(event) || '(no stack trace provided)',
  ].filter((part): part is string => part !== null);
  return maskPii(parts.join('\n'));
}

export function buildTriageSlackMessage(analysis: string, event: SentryEvent): unknown {
  const link = event.web_url ?? event.issue_url;
  const title = event.title ?? event.metadata?.value ?? 'Sentry issue';
  const headerTitle = link ? `<${link}|${escapeMrkdwn(title)}>` : escapeMrkdwn(title);
  const normalized = toSlackMrkdwn(analysis);
  const body = normalized.length > 2800 ? `${normalized.slice(0, 2800)}…` : normalized;
  return {
    blocks: [
      { type: 'header', text: { type: 'plain_text', text: '🔍 AI Triage', emoji: true } },
      { type: 'context', elements: [{ type: 'mrkdwn', text: headerTitle }] },
      { type: 'section', text: { type: 'mrkdwn', text: body } },
    ],
  };
}

interface GroqResponse {
  choices?: Array<{ message?: { content?: string } }>;
}

async function callGroq(env: Env, userPrompt: string): Promise<string | null> {
  const model = env.GROQ_MODEL ?? 'openai/gpt-oss-120b';
  const resp = await fetch('https://api.groq.com/openai/v1/chat/completions', {
    method: 'POST',
    headers: {
      authorization: `Bearer ${env.GROQ_API_KEY}`,
      'content-type': 'application/json',
    },
    body: JSON.stringify({
      model,
      temperature: 0.2,
      // Generous budget: Groq reasoning models (e.g. gpt-oss) spend tokens on
      // hidden reasoning before the visible answer; too low leaves content empty.
      max_tokens: 1500,
      messages: [
        { role: 'system', content: TRIAGE_SYSTEM_PROMPT },
        { role: 'user', content: userPrompt },
      ],
    }),
  });
  if (!resp.ok) {
    console.log(`Groq API error: ${resp.status}`);
    return null;
  }
  const data = (await resp.json()) as GroqResponse;
  const content = data.choices?.[0]?.message?.content?.trim();
  return content && content.length > 0 ? content : null;
}

/** Redact obvious secrets/PII before sending event text to the LLM. */
export function maskPii(text: string): string {
  return text
    .replace(/[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}/g, '<email>')
    .replace(/\b(gsk_|sk-|xoxb-|xoxp-|ghp_|github_pat_)[A-Za-z0-9_-]{8,}\b/g, '<token>')
    .replace(/Bearer\s+[A-Za-z0-9._-]{8,}/gi, 'Bearer <token>');
}

function toSlackMrkdwn(text: string): string {
  // Slack renders *bold*, not **bold**; collapse standard markdown bold.
  return text.replace(/\*\*/g, '*');
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
