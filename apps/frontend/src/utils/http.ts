import axios, {
  AxiosError,
  AxiosHeaders,
  type AxiosInstance,
  type AxiosResponse,
  type InternalAxiosRequestConfig,
  type AxiosRequestConfig,
} from 'axios';
import { notification } from 'antd';
import { buildCsrfHeader } from './csrf';
import type { ApiResponse } from '@/types/api';

// Environment variables can be loaded from import.meta.env (Vite)
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api';
const API_TIMEOUT = parseInt(import.meta.env.VITE_API_TIMEOUT || '15000');

const instance = axios.create({
  baseURL: API_BASE_URL,
  timeout: API_TIMEOUT,
  withCredentials: true, // Critical for cookie-based auth
  xsrfCookieName: 'XSRF-TOKEN',
  xsrfHeaderName: 'X-XSRF-TOKEN',
  headers: {
    'Content-Type': 'application/json',
  },
});

/**
 * 客製化 Axios 實例
 * 
 * 1. 自動處理 ApiResponse<T> 解包：直接回傳 T
 * 2. 強化型別定義：
 *    - T: 回傳資料的型別 (Response Data Type)
 *    - D: 請求資料的型別 (Request Payload Type)
 *    - R: 最終 Promise 回傳的型別 (預設為 T)
 */
export const http = instance as unknown as Omit<AxiosInstance, 'get' | 'delete' | 'post' | 'put' | 'patch'> & {
  get<T = unknown, R = T>(url: string, config?: AxiosRequestConfig): Promise<R>;
  delete<T = unknown, R = T>(url: string, config?: AxiosRequestConfig): Promise<R>;
  post<T = unknown, R = T, D = unknown>(url: string, data?: D, config?: AxiosRequestConfig): Promise<R>;
  put<T = unknown, R = T, D = unknown>(url: string, data?: D, config?: AxiosRequestConfig): Promise<R>;
  patch<T = unknown, R = T, D = unknown>(url: string, data?: D, config?: AxiosRequestConfig): Promise<R>;
};

instance.interceptors.request.use(async (config) => {
  const csrfHeader = await buildCsrfHeader(config.method);
  if (Object.keys(csrfHeader).length > 0) {
    if (!config.headers) {
      config.headers = new AxiosHeaders();
    }
    if (config.headers instanceof AxiosHeaders) {
      for (const [name, value] of Object.entries(csrfHeader)) {
        config.headers.set(name, value);
      }
    } else {
      Object.assign(config.headers, csrfHeader);
    }
  }
  return config;
});

// Response Interceptor
instance.interceptors.response.use(
  (response: AxiosResponse<ApiResponse<unknown> | unknown>) => {
    // 自動提取 ApiResponse<T>.data
    const res = response.data;
    
    // 檢查是否為標準 ApiResponse 結構
    if (res && typeof res === 'object' && 'success' in res) {
      // 強制轉型為 ApiResponse<unknown> 以存取 properties
      const apiRes = res as ApiResponse<unknown>;
      
      if (apiRes.success === true) {
        return apiRes.data as unknown as AxiosResponse;
      }
      
      // 如果 success 為 false，則視為錯誤，進入 reject
      const error = new Error(apiRes.error?.message || 'API Request Failed');
      error.cause = apiRes;
      return Promise.reject(error);
    }
    
    // 若非標準結構 (例如 blob 或第三方 API)，直接回傳
    return res as unknown as AxiosResponse;
  },
  async (error: AxiosError) => {
    const config = error.config as InternalAxiosRequestConfig & { retryCount?: number };
    const status = error.response?.status;
    
    // Retry logic
    const MAX_RETRIES = 3;
    const isNetworkError = !error.response && error.code !== 'ECONNABORTED'; // Network error (no response) but not timeout (unless we want to retry timeout too)
    const isRetryableStatus = status === 408 || (status && status >= 500 && status < 600); // 408 Timeout, 5xx Server Error
    
    if (config && (config.retryCount || 0) < MAX_RETRIES && (isNetworkError || isRetryableStatus)) {
      config.retryCount = (config.retryCount || 0) + 1;
      const delayMs = Math.pow(2, config.retryCount) * 1000; // Exponential backoff: 2s, 4s, 8s
      
      await new Promise(resolve => setTimeout(resolve, delayMs));
      return instance(config);
    }

    const data = error.response?.data as { error?: { message?: string }; message?: string } | undefined;

    if (status === 401) {
      // 避免在登入頁無限重整
      if (!window.location.pathname.includes('/auth/login')) {
        window.location.href = '/auth/login?expired=true';
      }
    } else if (status === 429) {
      notification.warning({
        message: '請稍後再試',
        description: data?.error?.message || data?.message || '請求過於頻繁，請稍後再試',
      });
    } else if (status === 403) {
      notification.error({
        message: '無權限存取',
        description: '您沒有權限存取此資源。',
      });
    } else if (status && status >= 500) {
      notification.error({
        message: '伺服器錯誤',
        description: data?.error?.message || data?.message || '伺服器發生錯誤，請稍後再試。',
      });
    }

    return Promise.reject(error);
  }
);
