import { http } from '../utils/http';
import type { ApiResponse, PageResponse } from '../types/api';

export interface AiReport {
  reportId: string;
  reportType: 'INSTRUMENT' | 'PORTFOLIO' | 'GENERAL';
  portfolioId?: string;
  instrumentId?: string;
  ticker?: string;
  inputSummary?: string;
  outputText: string;
  createdAt: string;
}

export const aiApi = {
  // SSE usually requires EventSource or fetch, but defining endpoint here
  streamAnalysis: (data: { portfolioId?: string; instrumentId?: string; reportType: string; prompt: string }) =>
    http.post('/ai/analysis/stream', data),

  getReports: (page = 1, size = 20) =>
    http.get<PageResponse<AiReport>>('/ai/reports', { params: { page, size } }),

  getReport: (reportId: string) =>
    http.get<ApiResponse<AiReport>>(`/ai/reports/${reportId}`),
};
