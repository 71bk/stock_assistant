import { http } from '../utils/http';

export interface SyncResult {
  added: number;
  skipped: number;
}

export interface AdminKeyStatus {
  /** Whether the backend requires an admin key (one is configured server-side). */
  required: boolean;
  /** Whether the browser currently holds a valid admin key cookie. */
  active: boolean;
}

export const adminApi = {
  // Admin key is exchanged for an HttpOnly cookie; it is never stored in JS-readable storage.
  getKeyStatus: () => http.get<AdminKeyStatus>('/admin/session/key'),

  setAdminKey: (apiKey: string) => http.post<AdminKeyStatus>('/admin/session/key', { apiKey }),

  clearAdminKey: () => http.delete<void>('/admin/session/key'),

  syncInstruments: () => http.post<SyncResult>('/admin/instruments/sync', {}, {
    timeout: 120000, // Sync can take over 60s, setting 2m timeout
  }),

  syncWarrants: () => http.post<SyncResult>('/admin/instruments/sync-warrants', {}, {
    timeout: 120000,
  }),

  rebuildPositions: (portfolioId: number, instrumentId?: number) =>
    http.post<{
      portfolioId: number;
      userId: number;
      targetInstrumentCount: number;
      rebuiltInstrumentCount: number;
      failedInstrumentCount: number;
      failedInstrumentIds: number[];
    }>('/admin/portfolios/positions-rebuild', { portfolioId, instrumentId }),

  snapshotValuations: (portfolioId?: number, userId?: number, asOfDate?: string) =>
    http.post<{
      asOfDate: string;
      total: number;
      succeeded: number;
      failed: number;
      failedPortfolioIds: number[];
    }>('/admin/portfolios/valuations-snapshot', { portfolioId, userId, asOfDate }),
};
