import { create } from 'zustand';
import type { User } from '../types/domain';
import { authApi, type LoginRequest } from '../api/auth.api';

const ADMIN_SESSION_HINT_KEY = 'admin_session_hint';

function readAdminSessionHint(): boolean {
  try {
    return window.sessionStorage.getItem(ADMIN_SESSION_HINT_KEY) === '1';
  } catch {
    return false;
  }
}

function writeAdminSessionHint(enabled: boolean): void {
  try {
    if (enabled) {
      window.sessionStorage.setItem(ADMIN_SESSION_HINT_KEY, '1');
      return;
    }
    window.sessionStorage.removeItem(ADMIN_SESSION_HINT_KEY);
  } catch {
    // Ignore storage failures (private mode / SSR-like env)
  }
}

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
      writeAdminSessionHint(true);
      set({ isAdmin: true });
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
      writeAdminSessionHint(false);
      set({ user: null, isAuthenticated: false, isAdmin: false });
      window.location.href = '/auth/login';
    }
  },

  checkAuth: async () => {
    set({ isLoading: true });
    try {
      // Call /auth/me to validate session and get user info
      const userData = await authApi.getMe();
      const isRoleAdmin = userData.role === 'ADMIN';
      const adminHint = readAdminSessionHint();
      set({
        user: userData,
        isAuthenticated: true,
        isAdmin: isRoleAdmin || adminHint,
      });
    } catch (error) {
      console.error(error);
      // If 401/403, we are not authenticated
      writeAdminSessionHint(false);
      set({ user: null, isAuthenticated: false, isAdmin: false });
    } finally {
      set({ isLoading: false });
    }
  },
}));
