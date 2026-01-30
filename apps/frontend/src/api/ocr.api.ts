import { http } from '../utils/http';
import type { ApiResponse } from '../types/api';

export interface DraftTrade {
  draftId: string;
  instrumentId: string | null;
  rowHash?: string;
  rawTicker: string;
  name: string;
  tradeDate: string;
  side: 'BUY' | 'SELL';
  quantity: number;
  price: number;
  currency: string;
  fee: number;
  tax: number;
  confidence?: number;
  warnings: string[];
  status?: 'VALID' | 'WARNING' | 'ERROR';
  errors: string[];
}

export interface OcrJob {
  id: string;
  status: 'QUEUED' | 'RUNNING' | 'DONE' | 'FAILED';
  progress: number;
  error?: string;
  statementId?: string;
  result?: {
    trades: DraftTrade[];
  };
}

export const ocrApi = {
  uploadFileOnly: async (file: File) => {
    const formData = new FormData();
    formData.append('file', file);
    const fileRes = (await http.post<ApiResponse<{ fileId: string }>>('/files', formData, {
      headers: {
        'Content-Type': undefined,
      },
      timeout: 60000,
    })) as unknown as ApiResponse<{ fileId: string }>;
    return fileRes.data.fileId || (fileRes.data as any).file_id;
  },

  // Deprecated: Use uploadFileOnly + createOcrJob
  upload: async (file: File, portfolioId: string = 'default') => {
    const fileId = await ocrApi.uploadFileOnly(file);
    return ocrApi.createOcrJob(fileId, portfolioId);
  },

  createOcrJob: (fileId: string, portfolioId: string, force = false) =>
    http.post<ApiResponse<{ jobId: string }>>('/ocr/jobs', {
      fileId,
      portfolioId,
      force,
    }),

  getJob: (jobId: string) =>
    http.get<ApiResponse<OcrJob>>(`/ocr/jobs/${jobId}`),

  getDrafts: (jobId: string) =>
    http.get<ApiResponse<{ items: DraftTrade[] }>>(`/ocr/jobs/${jobId}/drafts`),

  updateDraft: (draftId: string, updates: Partial<DraftTrade>) =>
    http.patch<ApiResponse<DraftTrade>>(`/ocr/drafts/${draftId}`, updates),

  confirmImport: (jobId: string, statementId: string, trades: DraftTrade[]) =>
    http.post<ApiResponse<{ importedCount: number }>>(`/ocr/jobs/${jobId}/confirm`, {
      statementId,
      trades,
    }),
};
