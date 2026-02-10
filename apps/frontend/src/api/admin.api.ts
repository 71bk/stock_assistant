import { http } from '../utils/http';

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
    return http.post<SyncResult>('/admin/instruments/sync', {}, {
      headers,
      timeout: 120000, // Sync can take over 60s, setting 2m timeout
    });
  },

  syncWarrants: (adminKey?: string) => {
    const headers: Record<string, string> = {};
    if (adminKey) {
      headers['X-Admin-Key'] = adminKey;
    }
    return http.post<SyncResult>('/admin/instruments/sync-warrants', {}, {
      headers,
      timeout: 120000,
    });
  },

  rebuildPositions: (portfolioId: string, instrumentId?: string, adminKey?: string) => {
    const headers: Record<string, string> = {};
    if (adminKey) {
      headers['X-Admin-Key'] = adminKey;
    }
    return http.post<{
      portfolioId: number;
      userId: number;
      targetInstrumentCount: number;
      rebuiltInstrumentCount: number;
      failedInstrumentCount: number;
      failedInstrumentIds: number[];
    }>('/admin/portfolios/positions-rebuild', { portfolioId, instrumentId }, { headers });
  },

  snapshotValuations: (portfolioId?: string, userId?: string, asOfDate?: string, adminKey?: string) => {
    const headers: Record<string, string> = {};
    if (adminKey) {
      headers['X-Admin-Key'] = adminKey;
    }
    return http.post<{
      asOfDate: string;
      total: number;
      succeeded: number;
      failed: number;
      failedPortfolioIds: number[];
    }>('/admin/portfolios/valuations-snapshot', { portfolioId, userId, asOfDate }, { headers });
  },
};
