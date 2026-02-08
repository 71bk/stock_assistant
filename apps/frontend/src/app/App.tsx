import React, { useEffect } from 'react';
import { App as AntApp } from 'antd';
import { RouterProvider } from 'react-router-dom';
import { antd as antdGlobals } from '../utils/antd-globals';
import { router } from '../router';
import { useAuthStore } from '../stores/auth.store';
import { ErrorBoundary } from '../components/common/ErrorBoundary';
import { AppProviders } from './AppProviders';
import 'dayjs/locale/zh-tw';

const AntdGlobalInitializer: React.FC = () => {
    const { message, modal, notification } = AntApp.useApp();
    
    useEffect(() => {
        antdGlobals.setInstances(message, modal, notification);
    }, [message, modal, notification]);
    
    return null;
};

const App: React.FC = () => {
  const { checkAuth } = useAuthStore();

  useEffect(() => {
    // Initialize authentication check on app load
    checkAuth();
  }, [checkAuth]); // Only once on mount

  return (
    <AppProviders>
      <AntApp>
        <AntdGlobalInitializer />
        <ErrorBoundary>
          <RouterProvider router={router} />
        </ErrorBoundary>
      </AntApp>
    </AppProviders>
  );
};

export default App;