import { http } from '../utils/http';

export interface UserSettings {
  baseCurrency: string;
  displayTimezone: string;
  theme?: 'light' | 'dark';
  notifications?: boolean;
}

export const usersApi = {
  updateSettings: (settings: Partial<UserSettings>) =>
    http.patch<UserSettings>('/users/me/settings', settings),
};
