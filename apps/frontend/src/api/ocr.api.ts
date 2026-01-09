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
  upload: (file: File) => {
    const formData = new FormData();
    formData.append('file', file);
    return http.post<ApiResponse<{ jobId: string }>>('/ocr/upload', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
  },

  getJob: (jobId: string) => 
    http.get<ApiResponse<OcrJob>>(`/ocr/jobs/${jobId}`),

  confirmImport: (statementId: string, trades: DraftTrade[]) =>
    http.post<ApiResponse<{ importedCount: number }>>(`/ocr/confirm`, { statementId, trades }),
};
