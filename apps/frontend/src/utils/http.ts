import axios, { AxiosError, AxiosResponse } from "axios";
import { env } from "@/app/env";

/**
 * 全域 Axios 實例
 * - baseURL 指向後端 API
 * - withCredentials 帶上 Cookie（JWT 認證）
 */
export const http = axios.create({
  baseURL: env.API_BASE_URL,
  withCredentials: true,
  timeout: 15000,
});

/**
 * 請求攔截器
 */
http.interceptors.request.use(
  (config) => {
    // 可在此添加 authorization header 或其他請求前置處理
    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

/**
 * 回應攔截器
 */
http.interceptors.response.use(
  (response: AxiosResponse) => {
    return response;
  },
  (error: AxiosError) => {
    // 401: 未登入，導向登入頁
    if (error.response?.status === 401) {
      window.location.href = "/auth/login";
    }

    // 其他錯誤可在此統一處理
    return Promise.reject(error);
  }
);
