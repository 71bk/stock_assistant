import axios from "axios";

export const http = axios.create({
  baseURL: "/api",          // 走 Vite proxy 或 Nginx proxy
  withCredentials: true,    // 讓瀏覽器帶 Cookie
  timeout: 15000,
});
