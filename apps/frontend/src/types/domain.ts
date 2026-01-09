export interface User {
  id: string;
  email: string;
  displayName: string;
  pictureUrl?: string;
  preferences?: {
    baseCurrency?: string;
    timezone?: string;
  };
}

export interface AuthState {
  user: User | null;
  isAuthenticated: boolean;
  isLoading: boolean;
}

export interface ApiError {
  code: string;
  message: string;
  details?: Record<string, any>;
}
