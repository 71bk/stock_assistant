import { create } from "zustand";

interface UIState {
  sidebarCollapsed: boolean;
  theme: "light" | "dark";

  // Actions
  toggleSidebar: () => void;
  setSidebarCollapsed: (collapsed: boolean) => void;
  setTheme: (theme: "light" | "dark") => void;
}

/**
 * UI 狀態 store
 * 管理側欄、主題等 UI 相關狀態
 */
export const useUIStore = create<UIState>((set) => ({
  sidebarCollapsed: false,
  theme: "light",

  toggleSidebar: () =>
    set((state) => ({
      sidebarCollapsed: !state.sidebarCollapsed,
    })),

  setSidebarCollapsed: (collapsed) =>
    set({
      sidebarCollapsed: collapsed,
    }),

  setTheme: (theme) =>
    set({
      theme,
    }),
}));
