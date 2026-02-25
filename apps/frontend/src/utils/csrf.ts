const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api';
const DEFAULT_CSRF_COOKIE_NAME = 'XSRF-TOKEN';
const DEFAULT_CSRF_HEADER_NAME = 'X-XSRF-TOKEN';
const SAFE_METHODS = new Set(['GET', 'HEAD', 'OPTIONS', 'TRACE']);

interface ApiEnvelope<T> {
  data?: T;
}

interface CsrfBootstrapData {
  enabled?: boolean;
  headerName?: string;
  token?: string;
}

let csrfTokenCache: string | null = null;
let csrfHeaderNameCache = DEFAULT_CSRF_HEADER_NAME;
let csrfEnabledCache: boolean | null = null;
let bootstrapPromise: Promise<void> | null = null;

function isDomAvailable(): boolean {
  return typeof document !== 'undefined';
}

function buildCsrfEndpoint(): string {
  if (API_BASE_URL.endsWith('/')) {
    return `${API_BASE_URL}auth/csrf`;
  }
  return `${API_BASE_URL}/auth/csrf`;
}

function readCookie(name: string): string | null {
  if (!isDomAvailable()) {
    return null;
  }
  const parts = document.cookie
    .split(';')
    .map((part) => part.trim())
    .filter((part) => part.startsWith(`${name}=`));

  if (parts.length === 0) {
    return null;
  }

  return decodeURIComponent(parts[0].substring(name.length + 1));
}

function cacheTokenFromCookie(): void {
  const token = readCookie(DEFAULT_CSRF_COOKIE_NAME);
  if (token) {
    csrfTokenCache = token;
  }
}

async function bootstrapCsrf(): Promise<void> {
  const response = await fetch(buildCsrfEndpoint(), {
    method: 'GET',
    credentials: 'include',
    headers: {
      Accept: 'application/json',
    },
  });

  if (!response.ok) {
    throw new Error(`Failed to bootstrap CSRF token: ${response.status}`);
  }

  const payload = (await response.json().catch(() => null)) as ApiEnvelope<CsrfBootstrapData> | null;
  const data = payload?.data;
  if (data) {
    if (typeof data.enabled === 'boolean') {
      csrfEnabledCache = data.enabled;
    }
    if (typeof data.headerName === 'string' && data.headerName.trim() !== '') {
      csrfHeaderNameCache = data.headerName.trim();
    }
    if (typeof data.token === 'string' && data.token.trim() !== '') {
      csrfTokenCache = data.token.trim();
    }
  }

  cacheTokenFromCookie();
}

export function isSafeHttpMethod(method?: string): boolean {
  const normalized = (method ?? 'GET').toUpperCase();
  return SAFE_METHODS.has(normalized);
}

export function getCsrfHeaderName(): string {
  return csrfHeaderNameCache;
}

export async function ensureCsrfToken(): Promise<string | null> {
  if (csrfEnabledCache === false) {
    return null;
  }

  if (csrfTokenCache) {
    return csrfTokenCache;
  }

  cacheTokenFromCookie();
  if (csrfTokenCache) {
    return csrfTokenCache;
  }

  if (!bootstrapPromise) {
    bootstrapPromise = bootstrapCsrf()
      .catch(() => undefined)
      .finally(() => {
        bootstrapPromise = null;
      });
  }

  await bootstrapPromise;
  return csrfTokenCache;
}

export async function buildCsrfHeader(method?: string): Promise<Record<string, string>> {
  if (isSafeHttpMethod(method)) {
    return {};
  }

  const token = await ensureCsrfToken();
  if (!token) {
    return {};
  }

  return {
    [getCsrfHeaderName()]: token,
  };
}
