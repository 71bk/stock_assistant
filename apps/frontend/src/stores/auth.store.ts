import { create } from 'zustand';
import type { User } from '../types/domain';
import { authApi } from '../api/auth.api';

interface AuthState {
  user: User | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  login: () => void; // Triggered after successful OAuth callback
  logout: () => Promise<void>;
  checkAuth: () => Promise<void>;
}

export const useAuthStore = create<AuthState>((set, get) => ({
  user: null,
  isAuthenticated: false,
  isLoading: true, // Start loading to check session

  login: () => {
     // After Google OAuth redirect, we might re-fetch user
     if (!get().isAuthenticated) {
       set({ isAuthenticated: true });
       get().checkAuth();
     }
  },

  logout: async () => {
    try {
      await authApi.logout();
    } catch (e) {
      console.error('Logout failed', e);
    } finally {
      // Always clean up state even if API fails
      set({ user: null, isAuthenticated: false });
      window.location.href = '/login';
    }
  },

  checkAuth: async () => {
    set({ isLoading: true });
    try {
      // Call /auth/me to validate session and get user info
      const userData = await authApi.getMe();
      set({ user: userData, isAuthenticated: true });
    } catch (error) {
      console.error(error);
      // If 401/403, we are not authenticated
      set({ user: null, isAuthenticated: false });
    } finally {
      set({ isLoading: false });
    }
  },
}));
