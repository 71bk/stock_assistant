import React from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { useAuthStore } from '../stores/auth.store';
import { Spin, Button, Typography } from 'antd';

const { Text } = Typography;

// Interface for props if we want to wrap children
interface GuardProps {
  children: React.ReactNode;
}

export const RequireAuth: React.FC<GuardProps> = ({ children }) => {
  const { isAuthenticated, isLoading } = useAuthStore();
  const location = useLocation();
  const [timedOut, setTimedOut] = React.useState(false);

  React.useEffect(() => {
    let timer: number;
    if (isLoading) {
      timer = window.setTimeout(() => {
        setTimedOut(true);
      }, 5000); // 5 seconds timeout
    }
    return () => window.clearTimeout(timer);
  }, [isLoading]);

  if (isLoading) {
    if (timedOut) {
      return (
        <div style={{ height: '100vh', display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', gap: 16 }}>
          <Text type="secondary">認證檢查時間過長，請檢查網路連線或重新整理。</Text>
          <Button onClick={() => window.location.reload()}>重新整理</Button>
        </div>
      );
    }
    return <Spin fullscreen size="large" tip="載入中..." />;
  }

  if (!isAuthenticated) {
    return <Navigate to="/auth/login" state={{ from: location }} replace />;
  }

  return <>{children}</>;
};

export const PublicOnly: React.FC<GuardProps> = ({ children }) => {
  const { isAuthenticated, isLoading } = useAuthStore();
  const location = useLocation();

  if (isLoading) {
    return <Spin fullscreen size="large" tip="Checking session..." />;
  }

  if (isAuthenticated) {
    // If user is already logged in, redirect to dashboard or previous page
    const state = location.state as { from?: { pathname: string } } | null;
    const from = state?.from?.pathname || '/dashboard';
    return <Navigate to={from} replace />;
  }

  return <>{children}</>;
};

