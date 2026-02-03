import { http } from '../utils/http';
import type { ApiResponse } from '../types/api';
import type { User } from '../types/domain';

export const authApi = {
  // 1. 取得當前使用者 (Session Check)
  getMe: () =>
    http.get<ApiResponse<User>>('/auth/me'),

  // 2. 登出 (清除 Cookie & Session)
  logout: () =>
    http.post<ApiResponse<void>>('/auth/logout'),

  // 3. (Optional) 手動刷新 Token，通常由 HttpOnly Cookie 自動處理，
  // 但若有需要主動換發可呼叫此 API
  refresh: () =>
    http.post<ApiResponse<void>>('/auth/refresh'),

  getSessions: () =>
    http.get<ApiResponse<any[]>>('/auth/sessions'),

  revokeSession: (sessionId: string) =>
    http.delete<ApiResponse<void>>(`/auth/sessions/${sessionId}`),
};

// 用於前端跳轉的 URL (非 AJAX 呼叫)
export const GOOGLE_LOGIN_URL = `${import.meta.env.VITE_API_BASE_URL || '/api'}/oauth2/authorization/google`;