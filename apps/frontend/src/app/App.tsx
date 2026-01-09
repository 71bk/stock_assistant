import React, { useEffect } from 'react';
import { ConfigProvider, App as AntApp, theme as antdThemeAlgorithm } from 'antd';
import { RouterProvider } from 'react-router-dom';
import { router } from '../router';
import { useUIStore } from '../stores/ui.store';
import { useAuthStore } from '../stores/auth.store';
import { antdTheme } from '../styles/antd-theme';
import 'dayjs/locale/zh-tw';

const App: React.FC = () => {
  const { theme } = useUIStore();
  const { checkAuth } = useAuthStore();

  useEffect(() => {
    // Initialize authentication check on app load
    checkAuth();
  }, [checkAuth]);

  return (
    <ConfigProvider
      theme={{
        ...antdTheme,
        algorithm: theme === 'dark' ? antdThemeAlgorithm.darkAlgorithm : antdThemeAlgorithm.defaultAlgorithm,
      }}
    >
      <AntApp>
        <RouterProvider router={router} />
      </AntApp>
    </ConfigProvider>
  );
};

export default App;