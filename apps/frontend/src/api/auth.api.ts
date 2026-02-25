import { http } from '../utils/http';
import type { User } from '../types/domain';

const DEFAULT_REFRESH_BACKOFF_MS = 60_000;
let refreshBlockedUntil = 0;

export const authApi = {
  // 1. 取得當前使用者 (Session Check)
  getMe: () =>
    http.get<User>('/auth/me'),

  // 2. 登出 (清除 Cookie & Session)
  logout: () =>
    http.post<void>('/auth/logout'),

  // 3. (Optional) 手動刷新 Token，通常由 HttpOnly Cookie 自動處理，
  // 但若有需要主動換發可呼叫此 API
  refresh: async () => {
    const now = Date.now();
    if (now < refreshBlockedUntil) {
      const waitSeconds = Math.ceil((refreshBlockedUntil - now) / 1000);
      throw new Error(`Refresh rate limited. Retry after ${waitSeconds}s`);
    }
    try {
      return await http.post<void>('/auth/refresh');
    } catch (error) {
      if (isRateLimited(error)) {
        refreshBlockedUntil = Date.now() + getRetryAfterMs(error);
      }
      throw error;
    }
  },

  // 4. Admin 本地登入 (Argon2id)
  loginAdmin: (data: LoginRequest) =>
    http.post<void>('/auth/admin/login', data),
};

export interface LoginRequest {
  email: string;
  password: string;
}

// 用於前端跳轉的 URL (非 AJAX 呼叫)
export const GOOGLE_LOGIN_URL = `${import.meta.env.VITE_API_BASE_URL || '/api'}/oauth2/authorization/google`;

function isRateLimited(error: unknown): boolean {
  const status = (error as { response?: { status?: number } })?.response?.status;
  return status === 429;
}

function getRetryAfterMs(error: unknown): number {
  const headers = (error as { response?: { headers?: Record<string, string> } })?.response?.headers;
  const retryAfter = headers?.['retry-after'];
  if (retryAfter) {
    const seconds = Number(retryAfter);
    if (!Number.isNaN(seconds) && seconds > 0) {
      return seconds * 1000;
    }
  }
  return DEFAULT_REFRESH_BACKOFF_MS;
}
