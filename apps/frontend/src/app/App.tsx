import React, { useEffect } from 'react';
import { App as AntApp, Alert } from 'antd';
import { RouterProvider } from 'react-router-dom';
import { antd as antdGlobals } from '../utils/antd-globals';
import { router } from '../router';
import { useAuthStore } from '../stores/auth.store';
import { ErrorBoundary } from '../components/common/ErrorBoundary';
import { AppProviders } from './AppProviders';
import { useOnlineStatus } from '../hooks/useOnlineStatus';
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
        <OfflineAlert />
        <ErrorBoundary>
          <RouterProvider router={router} />
        </ErrorBoundary>
      </AntApp>
    </AppProviders>
  );
};

const OfflineAlert: React.FC = () => {
  const isOnline = useOnlineStatus();
  if (isOnline) return null;
  return (
    <Alert
      message="網路連線已中斷"
      description="您目前處於離線狀態，部分功能可能無法使用。"
      type="warning"
      showIcon
      banner
      style={{ position: 'fixed', top: 0, left: 0, right: 0, zIndex: 9999 }}
    />
  );
};

export default App;