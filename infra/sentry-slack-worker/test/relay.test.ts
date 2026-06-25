import { describe, expect, it } from 'vitest';
import { buildSlackMessage, pickWebhookUrl, type Env } from '../src/index';

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
