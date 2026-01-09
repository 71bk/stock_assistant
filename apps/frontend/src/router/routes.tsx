import { RouteObject } from "react-router-dom";
import { Outlet } from "react-router-dom";
import { MainLayout } from "@/components/layout/MainLayout";
import { AuthLayout } from "@/components/layout/AuthLayout";
import { RequireAuth, PublicOnly } from "@/utils/guards";

// Pages
import Home from "@/pages/Home";
import Dashboard from "@/pages/Dashboard";
import Portfolio from "@/pages/Portfolio";
import Stocks from "@/pages/Stocks";
import ImportPage from "@/pages/Import";
import Login from "@/pages/Auth";
import OAuthCallback from "@/pages/Auth/Callback";

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
            element: <Home />,
          },
          {
            path: "dashboard",
            element: <Dashboard />,
          },
          {
            path: "portfolio",
            element: <Portfolio />,
          },
          {
            path: "stocks",
            element: <Stocks />,
          },
          {
            path: "import",
            element: <ImportPage />,
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
            element: <Login />,
          },
          {
            path: "callback",
            element: <OAuthCallback />,
          },
        ],
      },
    ],
  },
];

