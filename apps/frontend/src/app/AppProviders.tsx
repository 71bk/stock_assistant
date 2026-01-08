import React from "react";
import { ConfigProvider } from "antd";
import { QueryClientProvider, QueryClient } from "@tanstack/react-query";
import zhCN from "antd/locale/zh_CN";
import { antdTheme } from "@/styles/antd-theme";

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: 1,
      gcTime: 5 * 60 * 1000, // 5 minutes
    },
  },
});

interface AppProvidersProps {
  children: React.ReactNode;
}

/**
 * 集中放置所有 Provider
 * - AntD ConfigProvider
 * - React Query
 * - i18n（可選）
 */
export function AppProviders({ children }: AppProvidersProps) {
  return (
    <QueryClientProvider client={queryClient}>
      <ConfigProvider
        locale={zhCN}
        theme={{
          token: antdTheme,
        }}
      >
        {children}
      </ConfigProvider>
    </QueryClientProvider>
  );
}
