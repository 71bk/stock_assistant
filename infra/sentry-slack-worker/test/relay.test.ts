import { describe, expect, it } from 'vitest';
import {
  buildSlackMessage,
  buildTriagePrompt,
  buildTriageSlackMessage,
  extractIssueId,
  formatStack,
  maskPii,
  pickWebhookUrl,
  type Env,
} from '../src/index';

const env: Env = {
  SENTRY_CLIENT_SECRET: 'secret',
  SLACK_WEBHOOK_PROD: 'https://hooks.slack.com/prod',
  SLACK_WEBHOOK_DEV: 'https://hooks.slack.com/dev',
};

describe('pickWebhookUrl', () => {
  it('routes production environments to the prod channel', () => {
    expect(pickWebhookUrl('production', env)).toBe(env.SLACK_WEBHOOK_PROD);
    expect(pickWebhookUrl('PROD', env)).toBe(env.SLACK_WEBHOOK_PROD);
  });

  it('routes non-prod environments to the dev channel', () => {
    expect(pickWebhookUrl('dev', env)).toBe(env.SLACK_WEBHOOK_DEV);
    expect(pickWebhookUrl('staging', env)).toBe(env.SLACK_WEBHOOK_DEV);
  });

  it('falls back to prod when no dev channel is configured', () => {
    const noDev: Env = { ...env, SLACK_WEBHOOK_DEV: undefined };
    expect(pickWebhookUrl('dev', noDev)).toBe(env.SLACK_WEBHOOK_PROD);
    expect(pickWebhookUrl(undefined, noDev)).toBe(env.SLACK_WEBHOOK_PROD);
  });
});

describe('buildSlackMessage', () => {
  it('links the title to the Sentry event and includes context', () => {
    const message = buildSlackMessage(
      {
        title: 'NullPointerException',
        culprit: 'tw.bk.appapi.Foo.bar',
        level: 'error',
        environment: 'production',
        web_url: 'https://sentry.io/issues/1/',
      },
      'New issue',
    ) as { blocks: Array<{ type: string; text?: { text: string }; elements?: Array<{ text: string }> }> };

    const json = JSON.stringify(message);
    expect(json).toContain('https://sentry.io/issues/1/');
    expect(json).toContain('NullPointerException');
    expect(json).toContain('production');
    expect(json).toContain('New issue');
    expect(message.blocks[0].text?.text).toContain('production');
  });

  it('escapes Slack mrkdwn special characters', () => {
    const message = buildSlackMessage({
      title: 'bad <script> & "quotes"',
      level: 'warning',
      environment: 'dev',
    }) as { blocks: Array<{ text?: { text: string } }> };

    const json = JSON.stringify(message);
    expect(json).toContain('&lt;script&gt;');
    expect(json).toContain('&amp;');
  });
});

describe('extractIssueId', () => {
  it('uses issue_id when present', () => {
    expect(extractIssueId({ issue_id: 42 })).toBe('42');
  });

  it('falls back to parsing the issue/web url', () => {
    expect(extractIssueId({ issue_url: 'https://sentry.io/api/0/issues/12345/' })).toBe('12345');
    expect(extractIssueId({ web_url: 'https://sentry.io/org/proj/issues/999/events/abc/' })).toBe('999');
  });

  it('returns null when nothing identifies the issue', () => {
    expect(extractIssueId({ title: 'x' })).toBeNull();
  });
});

describe('formatStack + buildTriagePrompt', () => {
  const event = {
    title: 'NPE',
    level: 'error',
    environment: 'production',
    culprit: 'tw.bk.Foo.bar',
    exception: {
      values: [
        {
          type: 'NullPointerException',
          value: 'cannot invoke method on null',
          stacktrace: {
            frames: [
              { module: 'java.util.Lib', function: 'noise', lineno: 1, in_app: false },
              {
                module: 'tw.bk.Foo',
                function: 'bar',
                filename: 'Foo.java',
                lineno: 42,
                in_app: true,
                context_line: 'return x.size();',
              },
            ],
          },
        },
      ],
    },
  };

  it('prefers in-app frames and includes the exception + context line', () => {
    const stack = formatStack(event);
    expect(stack).toContain('NullPointerException: cannot invoke method on null');
    expect(stack).toContain('tw.bk.Foo.bar');
    expect(stack).toContain('Foo.java:42');
    expect(stack).toContain('return x.size();');
    expect(stack).not.toContain('java.util.Lib'); // library frame filtered out
  });

  it('masks PII / secrets in the prompt', () => {
    const prompt = buildTriagePrompt({
      title: 'failed for user john@example.com with token gsk_abcdefgh12345678',
      exception: { values: [] },
    });
    expect(prompt).toContain('<email>');
    expect(prompt).toContain('<token>');
    expect(prompt).not.toContain('john@example.com');
    expect(prompt).not.toContain('gsk_abcdefgh12345678');
  });
});

describe('buildTriageSlackMessage', () => {
  it('labels the message, links the issue, and normalizes bold', () => {
    const message = buildTriageSlackMessage('**Root cause:** null deref.', {
      title: 'NPE',
      web_url: 'https://sentry.io/issues/1/',
    });
    const json = JSON.stringify(message);
    expect(json).toContain('AI Triage');
    expect(json).toContain('https://sentry.io/issues/1/');
    expect(json).toContain('*Root cause:*');
    expect(json).not.toContain('**Root cause:**');
  });
});

describe('maskPii', () => {
  it('redacts emails, api keys, and bearer tokens', () => {
    expect(maskPii('mail a@b.co key sk-ABCDEFGH12345 Bearer abcdefgh1234'))
      .toBe('mail <email> key <token> Bearer <token>');
  });
});
