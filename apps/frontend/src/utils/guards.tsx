import React from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { useAuthStore } from '../stores/auth.store';
import { Spin } from 'antd';

// Interface for props if we want to wrap children
interface GuardProps {
  children: React.ReactNode;
}

export const RequireAuth: React.FC<GuardProps> = ({ children }) => {
  const { isAuthenticated, isLoading } = useAuthStore();
  const location = useLocation();

  if (isLoading) {
    return <Spin fullscreen size="large" tip="Loading..." />;
  }

  if (!isAuthenticated) {
    return <Navigate to="/login" state={{ from: location }} replace />;
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

