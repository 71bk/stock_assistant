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

export const useAuthStore = create<AuthState>((set) => ({
  user: null,
  isAuthenticated: false,
  isLoading: true, // Start loading to check session

  login: () => {
     // After Google OAuth redirect, we might re-fetch user
     set({ isAuthenticated: true });
     useAuthStore.getState().checkAuth();
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
      // const response = await authApi.getMe();
      // // Ensure the response data matches the User interface
      // const userData = response.data; // Type assertion might be needed if API response is not perfectly aligned
      // set({ user: userData, isAuthenticated: true });
      
      // Mock for development to bypass backend requirement
      await new Promise(resolve => setTimeout(resolve, 300));
      set({ 
        user: { 
          id: 'mock-user-1', 
          email: 'user@example.com', 
          displayName: 'Demo User',
          pictureUrl: 'https://api.dicebear.com/7.x/avataaars/svg?seed=Felix',
          preferences: { 
            baseCurrency: 'TWD',
            timezone: 'Asia/Taipei'
          }
        }, 
        isAuthenticated: true 
      });

    } catch (error) {
      console.error(error);
      // If 401/403, we are not authenticated
      set({ user: null, isAuthenticated: false });
    } finally {
      set({ isLoading: false });
    }
  },
}));
