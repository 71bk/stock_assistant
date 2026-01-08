import { RouterProvider } from "react-router-dom";
import { router } from "@/router";
import { AppProviders } from "./AppProviders";

/**
 * App Shell
 * 組裝所有 Provider 與路由
 */
export function App() {
  return (
    <AppProviders>
      <RouterProvider router={router} />
    </AppProviders>
  );
}
