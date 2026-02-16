/**
 * SSE (Server-Sent Events) Streaming Utility
 */

export interface SseOptions {
  url: string;
  method?: 'GET' | 'POST';
  body?: Record<string, unknown>;
  headers?: Record<string, string>;
  onMeta?: (payload?: Record<string, unknown>) => void;
  onDelta?: (text: string) => void;
  onDone?: (payload?: Record<string, unknown>) => void;
  onError?: (error: Error) => void;
  signal?: AbortSignal;
  maxRetries?: number;
  retryDelay?: number;
}

/**
 * Perform an SSE request with optional retry logic
 */
export async function fetchSseWithRetry(options: SseOptions): Promise<void> {
  const {
    url,
    method = 'POST',
    body,
    headers = {},
    onMeta,
    onDelta,
    onDone,
    onError,
    signal,
    maxRetries = 3,
    retryDelay = 2000,
  } = options;

  let retries = 0;
  let lastEventId: string | null = null;

  async function connect(): Promise<void> {
    try {
      const requestHeaders: Record<string, string> = {
        Accept: 'text/event-stream',
        ...headers,
      };

      if (body) {
        requestHeaders['Content-Type'] = requestHeaders['Content-Type'] ?? 'application/json';
      }
      if (lastEventId) {
        requestHeaders['Last-Event-ID'] = lastEventId;
      }

      const response = await fetch(url, {
        method,
        credentials: 'include',
        headers: requestHeaders,
        body: body ? JSON.stringify(body) : undefined,
        signal,
      });

      if (!response.ok) {
        const errData = await response.json().catch(() => ({}));
        throw new Error(errData?.error?.message || `Request failed with status ${response.status}`);
      }

      const reader = response.body?.getReader();
      const decoder = new TextDecoder();
      if (!reader) throw new Error('No reader available');

      let buffer = '';
      let streamDone = false;

      const processEvent = (rawEvent: string) => {
        const lines = rawEvent.split('\n');
        let eventType = 'message';
        let eventId: string | null = null;
        const dataLines: string[] = [];

        for (const line of lines) {
          if (line.startsWith('event:')) {
            eventType = line.slice(6).trim();
          } else if (line.startsWith('id:')) {
            eventId = line.slice(3).trim();
          } else if (line.startsWith('data:')) {
            dataLines.push(line.slice(5).trim());
          }
        }
        if (eventId) {
          lastEventId = eventId;
        }

        const dataStr = dataLines.join('\n');
        if (dataStr === '[DONE]') {
          streamDone = true;
          onDone?.();
          return;
        }

        if (eventType === 'error') {
          try {
            const err = JSON.parse(dataStr);
            throw new Error(err.message || 'Stream error');
          } catch {
            throw new Error(dataStr || 'Stream error');
          }
        }

        if (eventType === 'delta' || eventType === 'message') {
          try {
            const payload = JSON.parse(dataStr);
            if (payload.text) onDelta?.(payload.text);
          } catch {
            if (dataStr) onDelta?.(dataStr);
          }
        }

        if (eventType === 'meta') {
          try {
            const payload = JSON.parse(dataStr || '{}');
            onMeta?.(payload);
          } catch {
            onMeta?.();
          }
        }

        if (eventType === 'done') {
          const payload = JSON.parse(dataStr || '{}');
          onDone?.(payload);
          streamDone = true;
        }
      };

      while (!streamDone) {
        const { value, done: readerDone } = await reader.read();

        if (value) {
          buffer += decoder.decode(value, { stream: true });
        }

        if (readerDone) {
          buffer += decoder.decode();
          const parts = buffer.split('\n\n').filter(p => p.trim() !== '');
          for (const rawEvent of parts) {
            processEvent(rawEvent);
          }
          break;
        }

        const parts = buffer.split('\n\n');
        buffer = parts.pop() || '';

        for (const rawEvent of parts) {
          processEvent(rawEvent);
          if (streamDone) break;
        }
      }
    } catch (e: unknown) {
      if (e instanceof Error && e.name === 'AbortError') {
        return;
      }

      if (retries < maxRetries) {
        retries++;
        console.warn(`SSE connection failed, retrying (${retries}/${maxRetries})...`, e);
        await new Promise((resolve) => setTimeout(resolve, retryDelay));
        return connect();
      }

      const error = e instanceof Error ? e : new Error(String(e));
      onError?.(error);
      throw error;
    }
  }

  return connect();
}
