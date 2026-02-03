import { http } from '../utils/http';
import type { ApiResponse } from '../types/api';

export interface SyncResult {
  added: number;
  skipped: number;
}

export const adminApi = {
  syncInstruments: (adminKey?: string) => {
    const headers: Record<string, string> = {};
    if (adminKey) {
      headers['X-Admin-Key'] = adminKey;
    }
    return http.post<ApiResponse<SyncResult>>('/admin/instruments/sync', {}, {
      headers,
      timeout: 120000, // Sync can take over 60s, setting 2m timeout
    });
  },
};
