import { http } from '../utils/http';
import type { ApiResponse } from '../types/api';

export interface DraftTrade {
  id: string; // Temp ID (uuid)
  rowHash?: string;
  symbol: string;
  name?: string; // Optional inferred name
  tradeDate: string; // YYYY-MM-DD
  side: 'BUY' | 'SELL';
  quantity: number;
  price: number;
  currency: string;
  fee: number;
  tax: number;
  confidence: number; // 0.0 - 1.0
  warnings: string[];
  status: 'VALID' | 'WARNING' | 'ERROR'; // Helper for UI
}

export interface OcrJob {
  id: string;
  status: 'QUEUED' | 'RUNNING' | 'COMPLETED' | 'FAILED';
  progress: number;
  error?: string;
  statementId?: string;
  result?: {
    trades: DraftTrade[];
  };
}

export const ocrApi = {
  upload: async (file: File, portfolioId: string = 'default') => {
    // 1. Upload File
    const formData = new FormData();
    formData.append('file', file);
    const fileRes = (await http.post<ApiResponse<{ file_id: string }>>('/files', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })) as unknown as ApiResponse<{ file_id: string }>;

    // 2. Create OCR Job
    return http.post<ApiResponse<{ job_id: string }>>('/ocr/jobs', {
      file_id: fileRes.data.file_id,
      portfolio_id: portfolioId,
    });
  },

  getJob: (jobId: string) =>
    http.get<ApiResponse<OcrJob>>(`/ocr/jobs/${jobId}`),

  confirmImport: (jobId: string, statementId: string, trades: DraftTrade[]) =>
    http.post<ApiResponse<{ importedCount: number }>>(`/ocr/jobs/${jobId}/confirm`, {
      statementId,
      trades,
    }),
};
