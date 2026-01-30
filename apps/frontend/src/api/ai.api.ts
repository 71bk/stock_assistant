import { http } from '../utils/http';
import type { ApiResponse } from '../types/api';

export interface AiReport {
  reportId: string;
  title: string;
  content: string;
  createdAt: string;
}

export const aiApi = {
  // SSE usually requires EventSource or fetch, but defining endpoint here
  streamAnalysis: (data: { instrumentId?: string; prompt: string }) =>
    http.post('/ai/analysis/stream', data),

  getReports: () =>
    http.get<ApiResponse<AiReport[]>>('/ai/reports'),

  getReport: (reportId: string) =>
    http.get<ApiResponse<AiReport>>(`/ai/reports/${reportId}`),
};
