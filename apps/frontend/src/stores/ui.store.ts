import { create } from 'zustand';
import { persist } from 'zustand/middleware';

interface UIState {
  siderCollapsed: boolean;
  theme: 'light' | 'dark';
  chatVisible: boolean;
  toggleSider: () => void;
  setTheme: (theme: 'light' | 'dark') => void;
  setChatVisible: (visible: boolean) => void;
}

export const useUIStore = create<UIState>()(
  persist(
    (set) => ({
      siderCollapsed: false,
      theme: 'light',
      chatVisible: false,
      toggleSider: () => set((state) => ({ siderCollapsed: !state.siderCollapsed })),
      setTheme: (theme) => set({ theme }),
      setChatVisible: (visible) => set({ chatVisible: visible }),
    }),
    {
      name: 'ui-storage',
    }
  )
);