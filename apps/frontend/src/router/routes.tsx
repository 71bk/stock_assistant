import { RouteObject } from "react-router-dom";
import { App } from "@/app/App";
import { MainLayout } from "@/components/layout/MainLayout";
import { AuthLayout } from "@/components/layout/AuthLayout";

// Pages - 實現後再 import
// import Home from "@/pages/Home";
// import Dashboard from "@/pages/Dashboard";
// import Portfolio from "@/pages/Portfolio";
// import Stocks from "@/pages/Stocks";
// import Login from "@/pages/Auth/Login";

/**
 * 路由配置
 */
export const routeConfig: RouteObject[] = [
  {
    path: "/",
    element: <App />,
    children: [
      {
        path: "/",
        element: <MainLayout />,
        children: [
          {
            path: "",
            element: <div>Home</div>, // <Home />
          },
          {
            path: "dashboard",
            element: <div>Dashboard</div>, // <Dashboard />
          },
          {
            path: "portfolio",
            element: <div>Portfolio</div>, // <Portfolio />
          },
          {
            path: "stocks",
            element: <div>Stocks</div>, // <Stocks />
          },
        ],
      },
      {
        path: "auth",
        element: <AuthLayout />,
        children: [
          {
            path: "login",
            element: <div>Login</div>, // <Login />
          },
          {
            path: "callback",
            element: <div>Callback</div>, // <OAuthCallback />
          },
        ],
      },
    ],
  },
];
