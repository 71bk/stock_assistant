import { useAuthStore } from "@/stores/auth.store";

/**
 * 路由守護工具函數
 */

/**
 * 檢查是否已認證
 */
export const isAuthenticated = (): boolean => {
  const { isAuthenticated } = useAuthStore.getState();
  return isAuthenticated;
};

/**
 * 檢查是否需要重新登入
 * 若 401，跳轉至登入頁
 */
export const handleAuthError = (): void => {
  useAuthStore.getState().logout();
  window.location.href = "/auth/login";
};

/**
 * Protected route 檢查
 * 若未認證，導向登入頁
 */
export const checkAuthentication = (): boolean => {
  if (!isAuthenticated()) {
    window.location.href = "/auth/login";
    return false;
  }
  return true;
};
