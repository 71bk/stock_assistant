import { createBrowserRouter, Navigate } from 'react-router-dom';
import { MainLayout } from '../components/layout/MainLayout';
import { AuthLayout } from '../components/layout/AuthLayout';
import AuthCallback from '../pages/Auth/Callback';
import { RequireAuth } from '../utils/guards';
import Dashboard from '../pages/Dashboard';
import Portfolio from '../pages/Portfolio';
import ImportPage from '../pages/Import';

export const router = createBrowserRouter([
  {
    path: '/login',
    element: <AuthLayout />,
  },
  {
    path: '/auth/callback',
    element: <AuthCallback />,
  },
  {
    path: '/',
    element: <RequireAuth><MainLayout /></RequireAuth>,
    children: [
      { index: true, element: <Navigate to="/dashboard" replace /> },
      { path: 'dashboard', element: <Dashboard /> },
      { path: 'portfolio', element: <Portfolio /> },
      { path: 'trades', element: <div>Trades Page (Coming Soon)</div> },
      { path: 'import', element: <ImportPage /> },
      { path: 'reports', element: <div>Reports Page (Coming Soon)</div> },
      { path: 'settings', element: <div>Settings Page (Coming Soon)</div> },
    ],
  },
  {
    path: '*',
    element: <div>404 Not Found</div>,
  },
]);