import { http } from "../utils/http";

export interface User {
  id: string;
  email: string;
  display_name: string;
  picture_url?: string;
  base_currency: string;
  display_timezone: string;
}

export interface AuthResponse {
  success: boolean;
  data: User;
  error: null;
  traceId: string;
}

/**
 * 獲取當前登入使用者資訊
 */
export const getMe = async (): Promise<User> => {
  const response = await http.get<AuthResponse>("/auth/me");
  return response.data.data;
};

/**
 * 刷新 Token（旋轉）
 */
export const refreshToken = async (): Promise<User> => {
  const response = await http.post<AuthResponse>("/auth/refresh");
  return response.data.data;
};

/**
 * 登出
 */
export const logout = async (): Promise<void> => {
  await http.post("/auth/logout");
};

/**
 * 重定向到 Google OAuth 登入
 */
export const loginWithGoogle = (): void => {
  window.location.href = "/api/auth/google/login";
};
