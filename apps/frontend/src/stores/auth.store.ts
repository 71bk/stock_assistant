import { create } from 'zustand';
import type { User } from '../types/domain';
import { authApi, type LoginRequest } from '../api/auth.api';

interface AuthState {
  user: User | null;
  isAuthenticated: boolean;
  isAdmin: boolean;
  isLoading: boolean;
  login: () => void; // Triggered after successful OAuth callback
  loginAdmin: (data: LoginRequest) => Promise<void>;
  logout: () => Promise<void>;
  checkAuth: () => Promise<void>;
}

export const useAuthStore = create<AuthState>((set, get) => ({
  user: null,
  isAuthenticated: false,
  isAdmin: false,
  isLoading: true, // Start loading to check session

  login: () => {
    // After Google OAuth redirect, we might re-fetch user
    if (!get().isAuthenticated) {
      set({ isAuthenticated: true });
      get().checkAuth();
    }
  },

  loginAdmin: async (data: LoginRequest) => {
    set({ isLoading: true });
    try {
      await authApi.loginAdmin(data);
      // Login successful, fetch user data to update state
      await get().checkAuth();
    } finally {
      set({ isLoading: false });
    }
  },

  logout: async () => {
    try {
      await authApi.logout();
    } catch (e) {
      console.error('Logout failed', e);
    } finally {
      // Always clean up state even if API fails
      set({ user: null, isAuthenticated: false, isAdmin: false });
      window.location.href = '/auth/login';
    }
  },

  checkAuth: async () => {
    set({ isLoading: true });
    try {
      // Call /auth/me to validate session and get user info
      const userData = await authApi.getMe();
      set({
        user: userData,
        isAuthenticated: true,
        isAdmin: userData.role === 'ADMIN'
      });
    } catch (error) {
      console.error(error);
      // If 401/403, we are not authenticated
      set({ user: null, isAuthenticated: false, isAdmin: false });
    } finally {
      set({ isLoading: false });
    }
  },
}));
