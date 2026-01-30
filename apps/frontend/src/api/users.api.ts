import { http } from '../utils/http';
import type { ApiResponse } from '../types/api';

export interface UserSettings {
  baseCurrency: string;
  displayTimezone: string;
  theme?: 'light' | 'dark';
  notifications?: boolean;
}

export const usersApi = {
  updateSettings: (settings: Partial<UserSettings>) =>
    http.patch<ApiResponse<UserSettings>>('/users/me/settings', settings),
};
