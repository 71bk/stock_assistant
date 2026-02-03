import { http } from '../utils/http';
import type { ApiResponse } from '../types/api';

export interface DraftTrade {
  draftId: string;
  instrumentId: string | null;
  rowHash?: string;
  rawTicker: string;
  name: string;
  tradeDate: string;
  settlementDate: string | null;
  side: 'BUY' | 'SELL';
  quantity: string | number;
  price: string | number;
  currency: string;
  fee: string | number;
  tax: string | number;
  /** 客戶淨收/淨付金額（買入為負，賣出為正） */
  netAmount: number | null;
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
    return fileRes.data.fileId;
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

  confirmImport: (jobId: string, draftIds?: string[]) =>
    http.post<ApiResponse<{
      importedCount: number;
      errors: Array<{ draftId: string; reason: string }>;
    }>>(`/ocr/jobs/${jobId}/confirm`, draftIds?.length ? { draftIds } : {}),

  deleteDraft: (draftId: string) =>
    http.delete<ApiResponse<void>>(`/ocr/drafts/${draftId}`),
};
