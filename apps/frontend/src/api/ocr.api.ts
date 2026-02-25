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
  status: 'VALID' | 'WARNING' | 'ERROR';
  errors: string[];
  duplicate: boolean;
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
  fileId: string | null;
  alreadyExists: boolean;
}

const DUPLICATE_WARNING_CODES = new Set([
  'DUPLICATE_PORTFOLIO_TRADE',
  'DUPLICATE_IN_STATEMENT',
]);

function normalizeDraft(draft: DraftTrade): DraftTrade {
  const warnings = Array.isArray(draft.warnings) ? draft.warnings : [];
  const errors = Array.isArray(draft.errors) ? draft.errors : [];
  const duplicate = warnings.some((warning) => DUPLICATE_WARNING_CODES.has(warning));
  const status = errors.length > 0 ? 'ERROR' : warnings.length > 0 ? 'WARNING' : 'VALID';
  return {
    ...draft,
    warnings,
    errors,
    duplicate,
    status,
  };
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

  createOcrJob: (fileId: string, portfolioId: string, force = false) =>
    http.post<CreateOcrJobResponse>('/ocr/jobs', {
      fileId,
      portfolioId,
      force,
    }),

  getJob: (jobId: string) =>
    http.get<OcrJob>(`/ocr/jobs/${jobId}`),

  getDrafts: async (jobId: string): Promise<GetDraftsResponse> => {
    const response = await http.get<GetDraftsResponse>(`/ocr/jobs/${jobId}/drafts`);
    return {
      ...response,
      items: (response.items ?? []).map(normalizeDraft),
    };
  },

  updateDraft: async (draftId: string, updates: Partial<DraftTrade>): Promise<DraftTrade> => {
    const response = await http.patch<DraftTrade>(`/ocr/drafts/${draftId}`, updates);
    return normalizeDraft(response);
  },

  // PUT method for full update or if preferred over PATCH
  updateDraftPut: async (draftId: string, updates: Partial<DraftTrade>): Promise<DraftTrade> => {
    const response = await http.put<DraftTrade>(`/ocr/drafts/${draftId}`, updates);
    return normalizeDraft(response);
  },

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
