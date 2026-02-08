import { http } from '../utils/http';

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
  netAmount: string | null;
  confidence?: number;
  warnings: string[];
  status?: 'VALID' | 'WARNING' | 'ERROR';
  errors: string[];
  duplicate?: boolean;
}

export interface OcrJob {
  jobId: string;
  status: 'QUEUED' | 'RUNNING' | 'DONE' | 'FAILED' | 'CANCELLED';
  progress: number;
  errorMessage?: string | null;
  statementId?: string;
}

export interface CreateOcrJobResponse {
  jobId: string;
  statementId?: string;
  status: string;
}

export interface GetDraftsResponse {
  items: DraftTrade[];
}

export interface ConfirmImportResponse {
  importedCount: number;
  errors: Array<{ draftId: string; reason: string }>;
}

export interface PresignRequest {
  sha256: string;
  sizeBytes: number;
  contentType: string;
}

export interface PresignResponse {
  uploadUrl: string;
  method: string;
  headers: Record<string, string>;
  fileId: string;
  alreadyExists: boolean;
}

export const ocrApi = {
  uploadFileOnly: async (file: File) => {
    const formData = new FormData();
    formData.append('file', file);
    const res = await http.post<{ fileId: string; sha256: string }>('/files', formData, {
      headers: {
        'Content-Type': undefined,
      },
      timeout: 60000,
    });
    return res.fileId;
  },

  getPresignedUrl: (data: PresignRequest) =>
    http.post<PresignResponse>('/files/presign', data),

  // Deprecated: Use uploadFileOnly + createOcrJob
  upload: async (file: File, portfolioId: string = 'default') => {
    const fileId = await ocrApi.uploadFileOnly(file);
    return ocrApi.createOcrJob(fileId, portfolioId);
  },

  createOcrJob: (fileId: string, portfolioId: string, force = false) =>
    http.post<CreateOcrJobResponse>('/ocr/jobs', {
      fileId,
      portfolioId,
      force,
    }),

  getJob: (jobId: string) =>
    http.get<OcrJob>(`/ocr/jobs/${jobId}`),

  getDrafts: (jobId: string) =>
    http.get<GetDraftsResponse>(`/ocr/jobs/${jobId}/drafts`),

  updateDraft: (draftId: string, updates: Partial<DraftTrade>) =>
    http.patch<DraftTrade>(`/ocr/drafts/${draftId}`, updates),

  confirmImport: (jobId: string, draftIds?: string[]) =>
    http.post<ConfirmImportResponse>(`/ocr/jobs/${jobId}/confirm`, draftIds?.length ? { draftIds } : {}),

  deleteDraft: (draftId: string) =>
    http.delete<void>(`/ocr/drafts/${draftId}`),

  retryJob: (jobId: string, force = false) =>
    http.post<CreateOcrJobResponse>(`/ocr/jobs/${jobId}/retry`, {}, { params: { force } }),

  reparseJob: (jobId: string, force = false) =>
    http.post<CreateOcrJobResponse>(`/ocr/jobs/${jobId}/reparse`, {}, { params: { force } }),

  cancelJob: (jobId: string, force = false) =>
    http.post<OcrJob>(`/ocr/jobs/${jobId}/cancel`, {}, { params: { force } }),
};
