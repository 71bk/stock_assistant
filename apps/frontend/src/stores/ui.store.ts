import { create } from 'zustand';
import { persist } from 'zustand/middleware';

interface UIState {
  siderCollapsed: boolean;
  theme: 'light' | 'dark';
  toggleSider: () => void;
  setTheme: (theme: 'light' | 'dark') => void;
}

export const useUIStore = create<UIState>()(
  persist(
    (set) => ({
      siderCollapsed: false,
      theme: 'light',
      toggleSider: () => set((state) => ({ siderCollapsed: !state.siderCollapsed })),
      setTheme: (theme) => set({ theme }),
    }),
    {
      name: 'ui-storage',
    }
  )
);