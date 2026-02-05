import React from "react";
import { ConfigProvider, theme as antdThemeAlgorithm } from "antd";
import { QueryClientProvider, QueryClient } from "@tanstack/react-query";
import zhTW from "antd/locale/zh_TW";
import { antdTheme } from "@/styles/antd-theme";
import { useUIStore } from "@/stores/ui.store";

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
  const { theme } = useUIStore();

  return (
    <QueryClientProvider client={queryClient}>
      <ConfigProvider
        locale={zhTW}
        theme={{
          ...antdTheme,
          algorithm: theme === 'dark' ? antdThemeAlgorithm.darkAlgorithm : antdThemeAlgorithm.defaultAlgorithm,
        }}
      >
        {children}
      </ConfigProvider>
    </QueryClientProvider>
  );
}
