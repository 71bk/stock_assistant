import { http } from '../utils/http';
import type { PageData } from '../types/api';

export type AiReportType = 'INSTRUMENT' | 'PORTFOLIO' | 'GENERAL';

export interface AiReportSummary {
  reportId: string;
  reportType: AiReportType;
  portfolioId?: string;
  instrumentId?: string;
  createdAt: string;
}

export interface AiReportDetail extends AiReportSummary {
  inputSummary?: string;
  outputText: string;
}

export const aiApi = {
  // SSE usually requires EventSource or fetch, but defining endpoint here
  streamAnalysis: (data: { portfolioId?: string; instrumentId?: string; reportType: string; prompt: string }) =>
    http.post('/ai/analysis/stream', data),

  getReports: (page = 1, size = 20) =>
    http.get<PageData<AiReportSummary>>('/ai/reports', { params: { page, size } }),

  getReport: (reportId: string) =>
    http.get<AiReportDetail>(`/ai/reports/${reportId}`),
};
