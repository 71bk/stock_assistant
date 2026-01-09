import axios, { AxiosError } from 'axios';
import { notification } from 'antd';

// Environment variables can be loaded from import.meta.env (Vite)
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api';

export const http = axios.create({
  baseURL: API_BASE_URL,
  timeout: 10000,
  withCredentials: true, // Critical for cookie-based auth
  headers: {
    'Content-Type': 'application/json',
  },
});

// Response Interceptor
http.interceptors.response.use(
  (response) => {
    return response.data;
  },
  async (error: AxiosError) => {
    const status = error.response?.status;
    const data: any = error.response?.data;

    if (status === 401) {
      // Handle Unauthorized (e.g., redirect to login)
      // We rely on the router or auth store to handle the redirect
      // to avoid circular dependencies here.
      // You might emit a global event or check window.location
      if (!window.location.pathname.includes('/login')) {
        window.location.href = '/login?expired=true';
      }
    } else if (status === 403) {
      notification.error({
        message: 'Permission Denied',
        description: 'You do not have access to this resource.',
      });
    } else if (status && status >= 500) {
      notification.error({
        message: 'Server Error',
        description: data?.message || 'Something went wrong on the server.',
      });
    }

    return Promise.reject(error);
  }
);