import axios, { AxiosError, type AxiosInstance, type AxiosResponse, type InternalAxiosRequestConfig } from 'axios';
import { notification } from 'antd';

// Environment variables can be loaded from import.meta.env (Vite)
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api';
const API_TIMEOUT = parseInt(import.meta.env.VITE_API_TIMEOUT || '15000');

const instance = axios.create({
  baseURL: API_BASE_URL,
  timeout: API_TIMEOUT,
  withCredentials: true, // Critical for cookie-based auth
  headers: {
    'Content-Type': 'application/json',
  },
});

/**
 * 客製化 Axios 實例，使其直接回傳 data 內容而非 AxiosResponse
 */
export const http = instance as unknown as Omit<AxiosInstance, 'get' | 'delete' | 'post' | 'put' | 'patch'> & {
  get<T = unknown, R = T>(url: string, config?: unknown): Promise<R>;
  delete<T = unknown, R = T>(url: string, config?: unknown): Promise<R>;
  post<T = unknown, R = T, D = unknown>(url: string, data?: D, config?: unknown): Promise<R>;
  put<T = unknown, R = T, D = unknown>(url: string, data?: D, config?: unknown): Promise<R>;
  patch<T = unknown, R = T, D = unknown>(url: string, data?: D, config?: unknown): Promise<R>;
};

// Response Interceptor
instance.interceptors.response.use(
  (response: AxiosResponse) => {
    // 自動提取 ApiResponse<T>.data
    const res = response.data;
    if (res && typeof res === 'object' && 'success' in res) {
      if (res.success === true) {
        return res.data;
      }
      // 如果 success 為 false，則視為錯誤，進入 reject
      const error = new Error(res.error?.message || 'API Request Failed');
      error.cause = res;
      return Promise.reject(error);
    }
    return res;
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
