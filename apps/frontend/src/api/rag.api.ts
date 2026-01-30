import { http } from '../utils/http';
import type { ApiResponse } from '../types/api';

export const ragApi = {
  createDocument: (data: { rawText?: string; fileId?: string }) =>
    http.post<ApiResponse<any>>('/rag/documents', data),

  query: (data: { query: string }) =>
    http.post<ApiResponse<any>>('/rag/query', data),
};
