import { createBrowserRouter } from 'react-router-dom';
import { routeConfig } from './routes';
import { createBrowserRouter, Navigate } from 'react-router-dom';
import { MainLayout } from '../components/layout/MainLayout';
import { AuthLayout } from '../components/layout/AuthLayout';
import AuthCallback from '../pages/Auth/Callback';
import { RequireAuth } from '../utils/guards';
import Dashboard from '../pages/Dashboard';
import Portfolio from '../pages/Portfolio';
import ImportPage from '../pages/Import';
import Trades from '../pages/Trades';
import Reports from '../pages/Reports';
import Settings from '../pages/Settings';
import Stocks from '../pages/Stocks';

export const router = createBrowserRouter(routeConfig);
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
      { path: 'trades', element: <Trades /> },
      { path: 'stocks', element: <Stocks /> },
      { path: 'import', element: <ImportPage /> },
      { path: 'reports', element: <Reports /> },
      { path: 'settings', element: <Settings /> },
    ],
  },
  {
    path: '*',
    element: <div>404 Not Found</div>,
  },
]);