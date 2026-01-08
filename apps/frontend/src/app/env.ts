/**
 * 環境變數配置
 * 讀取 import.meta.env，統一管理與型別安全
 */

export const env = {
  // API
  API_BASE_URL: import.meta.env.VITE_API_BASE_URL || "/api",
  
  // Google OAuth
  GOOGLE_CLIENT_ID: import.meta.env.VITE_GOOGLE_CLIENT_ID || "",
  
  // 應用設定
  APP_NAME: import.meta.env.VITE_APP_NAME || "Stock Assistant",
  APP_VERSION: import.meta.env.VITE_APP_VERSION || "0.0.1",
  
  // 開發環境檢查
  isDev: import.meta.env.DEV,
  isProd: import.meta.env.PROD,
};

// 驗證必要的環境變數
if (env.isProd && !env.GOOGLE_CLIENT_ID) {
  console.warn("Warning: VITE_GOOGLE_CLIENT_ID is not set");
}
