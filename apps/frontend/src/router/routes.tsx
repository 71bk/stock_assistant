import { lazy } from "react";
import type { RouteObject } from "react-router-dom";
import { Outlet, Navigate } from "react-router-dom";
import { MainLayout } from "@/components/layout/MainLayout";
import { AdminLayout } from "@/components/layout/AdminLayout";
import { AuthLayout } from "@/components/layout/AuthLayout";
import { RequireAuth, RequireAdmin, PublicOnly } from "@/utils/guards";

// Pages (Lazy Loading)
const Dashboard = lazy(() => import("@/pages/Dashboard"));
const Portfolio = lazy(() => import("@/pages/Portfolio"));
const Trades = lazy(() => import("@/pages/Trades"));
const Reports = lazy(() => import("@/pages/Reports"));
const Settings = lazy(() => import("@/pages/Settings"));
const Stocks = lazy(() => import("@/pages/Stocks"));
const InstrumentDetail = lazy(() => import("@/pages/InstrumentDetail"));
const ImportPage = lazy(() => import("@/pages/Import"));
const ChatPage = lazy(() => import("@/pages/Chat"));
const KnowledgeBasePage = lazy(() => import("@/pages/KnowledgeBase"));
const Login = lazy(() => import("@/pages/Auth").then(module => ({ default: module.Login })));
const AdminLogin = lazy(() => import("@/pages/Auth/AdminLogin").then(module => ({ default: module.AdminLogin })));
const AdminOverview = lazy(() => import("@/pages/Admin/OverviewPage"));
const AdminUsers = lazy(() => import("@/pages/Admin/UsersPage"));
const AdminAiUsage = lazy(() => import("@/pages/Admin/AiUsagePage"));
const AdminApiTraffic = lazy(() => import("@/pages/Admin/ApiTrafficPage"));
const AdminMaintenance = lazy(() => import("@/pages/Admin/MaintenancePage"));
const OAuthCallback = lazy(() => import("@/pages/Auth/Callback"));

import { LazyWrapper } from "@/components/common/LazyWrapper";

/**
 * 路由配置
 */
export const routeConfig: RouteObject[] = [
  {
    path: "/",
    element: <Outlet />,
    children: [
      {
        path: "/",
        element: (
          <RequireAuth>
            <MainLayout />
          </RequireAuth>
        ),
        children: [
          {
            index: true,
            element: (
              <LazyWrapper>
                <Dashboard />
              </LazyWrapper>
            ),
          },
          {
            path: "dashboard",
            element: (
              <LazyWrapper>
                <Dashboard />
              </LazyWrapper>
            ),
          },
          {
            path: "portfolio",
            element: (
              <LazyWrapper>
                <Portfolio />
              </LazyWrapper>
            ),
          },
          {
            path: "trades",
            element: (
              <LazyWrapper>
                <Trades />
              </LazyWrapper>
            ),
          },
          {
            path: "reports",
            element: (
              <LazyWrapper>
                <Reports />
              </LazyWrapper>
            ),
          },
          {
            path: "settings",
            element: (
              <LazyWrapper>
                <Settings />
              </LazyWrapper>
            ),
          },
          {
            path: "stocks",
            element: (
              <LazyWrapper>
                <Stocks />
              </LazyWrapper>
            ),
          },
          {
            path: "instruments/:symbolKey",
            element: (
              <LazyWrapper>
                <InstrumentDetail />
              </LazyWrapper>
            ),
          },
          {
            path: "import",
            element: (
              <LazyWrapper>
                <ImportPage />
              </LazyWrapper>
            ),
          },
          {
            path: "chat",
            element: (
              <LazyWrapper>
                <ChatPage />
              </LazyWrapper>
            ),
          },
          {
            path: "knowledge-base",
            element: (
              <LazyWrapper>
                <KnowledgeBasePage />
              </LazyWrapper>
            ),
          },
        ],
      },
      {
        path: "admin",
        element: (
          <RequireAdmin>
            <AdminLayout />
          </RequireAdmin>
        ),
        children: [
          {
            index: true,
            element: <Navigate to="/admin/overview" replace />,
          },
          {
            // Backward-compat for old links (/admin/dashboard, /admin/analytics).
            path: "dashboard",
            element: <Navigate to="/admin/overview" replace />,
          },
          {
            path: "analytics",
            element: <Navigate to="/admin/users" replace />,
          },
          {
            path: "overview",
            element: (
              <LazyWrapper>
                <AdminOverview />
              </LazyWrapper>
            ),
          },
          {
            path: "users",
            element: (
              <LazyWrapper>
                <AdminUsers />
              </LazyWrapper>
            ),
          },
          {
            path: "ai-usage",
            element: (
              <LazyWrapper>
                <AdminAiUsage />
              </LazyWrapper>
            ),
          },
          {
            path: "api-traffic",
            element: (
              <LazyWrapper>
                <AdminApiTraffic />
              </LazyWrapper>
            ),
          },
          {
            path: "maintenance",
            element: (
              <LazyWrapper>
                <AdminMaintenance />
              </LazyWrapper>
            ),
          },
        ],
      },
      {
        path: "auth",
        element: (
          <PublicOnly>
            <AuthLayout />
          </PublicOnly>
        ),
        children: [
          {
            path: "login",
            element: (
              <LazyWrapper>
                <Login />
              </LazyWrapper>
            ),
          },
          {
            path: "admin/login",
            element: (
              <LazyWrapper>
                <AdminLogin />
              </LazyWrapper>
            ),
          },
          {
            path: "callback",
            element: (
              <LazyWrapper>
                <OAuthCallback />
              </LazyWrapper>
            ),
          },
        ],
      },
    ],
  },
];



