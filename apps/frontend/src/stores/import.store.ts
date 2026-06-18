import { create } from 'zustand';
import { ocrApi } from '../api/ocr.api';
import type { DraftTrade, OcrJob } from '../api/ocr.api';
import { msg, mdl } from '../utils/antd-globals';
import { logger } from '../utils/logger';

type ApiErrorInfo = {
  status?: number;
  code?: string;
  message?: string;
};

type ApiErrorLike = {
  response?: {
    status?: number;
    data?: {
      error?: {
        code?: string;
        message?: string;
      };
      message?: string;
    };
  };
  cause?: {
    error?: {
      code?: string;
      message?: string;
    };
    message?: string;
  };
  message?: string;
};

const extractApiErrorInfo = (error: unknown): ApiErrorInfo => {
  const err = error as ApiErrorLike;
  return {
    status: err?.response?.status,
    code: err?.response?.data?.error?.code ?? err?.cause?.error?.code,
    message: err?.response?.data?.error?.message
      ?? err?.response?.data?.message
      ?? err?.cause?.error?.message
      ?? err?.cause?.message
      ?? err?.message,
  };
};

interface ImportState {
  currentStep: number;
  activeJobId: string | null;
  fileId: string | null;
  jobStatus: OcrJob['status'] | null;
  errorMessage: string | null;
  progress: number;
  draftTrades: DraftTrade[];
  statementId: string | null;
  isPolling: boolean;
  isLoading: boolean;
  pollingIntervalId: number | null;
  activePollingJobId: string | null;

  uploadFile: (file: File, portfolioId: string) => Promise<void>;
  reprocessJob: (portfolioId: string) => Promise<void>;
  cancelJob: () => Promise<void>;
  pollJob: (jobId: string) => void;
  reset: () => void;
  updateDraftTrade: (id: string, updates: Partial<DraftTrade>) => Promise<void>;
  deleteDraftTrade: (id: string) => Promise<void>;
  confirmTrades: (selectedIds: string[]) => Promise<void>;
  setStep: (step: number) => void;
  providePassword: (password: string) => Promise<void>;
}

export const useImportStore = create<ImportState>((set, get) => ({
  currentStep: 0,
  activeJobId: null,
  fileId: null,
  jobStatus: null,
  errorMessage: null,
  progress: 0,
  draftTrades: [],
  statementId: null,
  isPolling: false,
  isLoading: false,
  pollingIntervalId: null,
  activePollingJobId: null,

  setStep: (step) => set({ currentStep: step }),

  reset: () =>
    set({
      currentStep: 0,
      activeJobId: null,
      fileId: null,
      jobStatus: null,
      errorMessage: null,
      progress: 0,
      draftTrades: [],
      statementId: null,
      pollingIntervalId: null,
      activePollingJobId: null,
    }),

  uploadFile: async (file, portfolioId) => {
    if (get().isLoading) return;
    try {
      set({ isLoading: true, currentStep: 1, progress: 0, jobStatus: 'QUEUED' });

      const fileId = await ocrApi.uploadFileOnly(file);
      set({ fileId });

      const res = await ocrApi.createOcrJob(fileId, portfolioId);
      const jobId = res.jobId;
      if (!jobId) throw new Error('Failed to create OCR job');

      set({ activeJobId: jobId, isPolling: true });
      get().pollJob(jobId);
    } catch (error) {
      logger.error('Upload file failed', error);
      set({ jobStatus: 'FAILED', isPolling: false });
      msg.error('Upload failed');
    } finally {
      set({ isLoading: false });
    }
  },

  reprocessJob: async (portfolioId) => {
    if (get().isLoading) return;
    const { activeJobId, fileId, pollingIntervalId } = get();

    try {
      set({ isLoading: true });
      if (pollingIntervalId) {
        clearInterval(pollingIntervalId);
      }

      set({ jobStatus: 'QUEUED', progress: 0, draftTrades: [], statementId: null, isPolling: true, pollingIntervalId: null, activePollingJobId: null });

      let jobId = activeJobId;
      if (jobId) {
        const res = await ocrApi.reparseJob(jobId, true);
        jobId = res.jobId;
      } else if (fileId) {
        const res = await ocrApi.createOcrJob(fileId, portfolioId, true);
        jobId = res.jobId;
      }

      if (!jobId) throw new Error('Failed to start OCR job');

      set({ activeJobId: jobId });
      get().pollJob(jobId);
    } catch (error) {
      logger.error('Reprocess job failed', error);
      set({ jobStatus: 'FAILED', isPolling: false });
      msg.error('重新辨識失敗');
    } finally {
      set({ isLoading: false });
    }
  },

  cancelJob: async () => {
    const { activeJobId, pollingIntervalId } = get();
    if (!activeJobId) return;

    try {
      await ocrApi.cancelJob(activeJobId);
      if (pollingIntervalId) {
          clearInterval(pollingIntervalId);
      }
      set({ jobStatus: 'CANCELLED', isPolling: false, pollingIntervalId: null, activePollingJobId: null });
      msg.info('任務已取消');
    } catch (error) {
      logger.error('Cancel job failed', error);
      msg.error('取消失敗');
    }
  },

  pollJob: (jobId: string) => {
    if (!jobId) return;

    const { pollingIntervalId, activePollingJobId } = get();
    if (activePollingJobId === jobId && pollingIntervalId) {
      return;
    }

    if (pollingIntervalId) {
      clearInterval(pollingIntervalId);
    }

    set({ activePollingJobId: jobId });

    const interval = setInterval(async () => {
      try {
        const job = await ocrApi.getJob(jobId);
  
        set({ jobStatus: job.status, progress: job.progress, errorMessage: job.errorMessage });
  
        if (job.status === 'DONE' || job.status === 'FAILED' || job.status === 'CANCELLED' || job.status === 'PASSWORD_REQUIRED' || job.status === 'PASSWORD_INVALID') {
          clearInterval(interval);
          set({ isPolling: false, pollingIntervalId: null, activePollingJobId: null });
          if (job.status === 'DONE') {
            try {
              const res = await ocrApi.getDrafts(jobId);
              const trades = res.items;
              set({
                draftTrades: trades,
                statementId: job.statementId || null,
              });
              msg.success('OCR Processing Complete');
            } catch (err) {
              logger.error('Failed to fetch drafts', err);
              msg.error('Failed to load drafts');
            }
          }
        }
      } catch (error) {
        logger.error('Polling job failed', error);
        clearInterval(interval);
        set({ isPolling: false, jobStatus: 'FAILED', pollingIntervalId: null });
      }
    }, 2000);

    set({ pollingIntervalId: interval as unknown as number });
  },

  updateDraftTrade: async (id, updates) => {
    if (get().isLoading) return;
    try {
      set({ isLoading: true });
      const updatedDraft = await ocrApi.updateDraft(id, updates);
      set((state) => ({
        draftTrades: state.draftTrades.map((t) =>
          t.draftId === id ? updatedDraft : t
        ),
      }));
    } catch (error) {
      logger.error('Update draft failed', error);
      msg.error('Failed to update draft');
    } finally {
      set({ isLoading: false });
    }
  },

  deleteDraftTrade: async (id) => {
    if (get().isLoading) return;
    try {
      set({ isLoading: true });
      await ocrApi.deleteDraft(id);
      set((state) => ({
        draftTrades: state.draftTrades.filter((t) => t.draftId !== id),
      }));
      msg.success('草稿已刪除');
    } catch (error) {
      logger.error('Delete draft failed', error);
      msg.error('刪除草稿失敗');
    } finally {
      set({ isLoading: false });
    }
  },

  confirmTrades: async (selectedIds) => {
    if (get().isLoading) return;
    const { activeJobId, statementId, draftTrades } = get();

    if (!statementId || !activeJobId) return;

    set({ isLoading: true });
    try {
      // Step 2: Call confirm with draftIds (removed statementId parameter)
      const res = await ocrApi.confirmImport(activeJobId, selectedIds);
      const { importedCount, errors } = res;

      // Step 3: Handle errors if any
      if (errors && errors.length > 0) {
        // Mark drafts with errors
        const errorMap = new Map(errors.map((e: { draftId: string; reason: string }) => [e.draftId, e.reason]));
        set((state) => ({
          draftTrades: state.draftTrades
            .map((t) => {
              const errorReason = errorMap.get(t.draftId);
              if (errorReason) {
                return {
                  ...t,
                  status: 'ERROR' as const,
                  errors: [...(t.errors || []), errorReason],
                };
              }

              // 若在 selectedIds 中但沒在 errorMap 中，代表成功導入，返回 null 稍後過濾掉
              if (selectedIds.includes(t.draftId)) {
                return null;
              }

              // 其他未選中的交易，原樣保留
              return t;
            })
            .filter((t): t is DraftTrade => t !== null),
        }));

        if (importedCount > 0) {
          msg.success(`已成功匯入 ${importedCount} 筆交易`);
        }
        if (errors.length > 0) {
          mdl.error({
            title: '部分交易匯入失敗',
            content: errors.map((err: { reason: string }) => err.reason).join('\n'),
            destroyOnHidden: true,
          });
        }
      } else {
        // All successful - remove imported drafts from local state
        const remainingDrafts = draftTrades.filter((t) => !selectedIds.includes(t.draftId));
        set({ draftTrades: remainingDrafts });

        // If no drafts left, go to success step
        if (remainingDrafts.length === 0) {
          set({ currentStep: 2 });
        } else {
          msg.success(`已成功匯入 ${importedCount} 筆交易`);
        }
      }
    } catch (e) {
      logger.error('Import confirmation failed', e);
      msg.error('匯入確認失敗，請確保已選取項目的標的代號正確');
    } finally {
      set({ isLoading: false });
    }
  },

  providePassword: async (password: string) => {
    const { activeJobId, jobStatus } = get();
    if (!activeJobId) return;
    if (jobStatus !== 'PASSWORD_REQUIRED' && jobStatus !== 'PASSWORD_INVALID') {
      get().pollJob(activeJobId);
      return;
    }

    set({ isLoading: true });
    try {
      const job = await ocrApi.providePassword(activeJobId, password);
      set({
        jobStatus: job.status,
        progress: job.progress,
        errorMessage: job.errorMessage ?? null,
      });

      if (job.status === 'PASSWORD_INVALID') {
        set({ errorMessage: job.errorMessage || '密碼錯誤，請重新輸入' });
        return;
      }

      if (job.status === 'PASSWORD_REQUIRED' || job.status === 'FAILED' || job.status === 'CANCELLED') {
        return;
      }

      // 後端已 enqueue，開始 polling 等待結果
      msg.info('密碼已送出，正在重新解析...');
      set({ isPolling: true, errorMessage: null });
      get().pollJob(activeJobId);
    } catch (error: unknown) {
      logger.error('Provide password failed', error);
      const apiError = extractApiErrorInfo(error);
      const message = apiError.message || '送出密碼失敗';
      const lowerMessage = message.toLowerCase();

      const isInvalid = apiError.code === 'PDF_PASSWORD_INVALID'
        || lowerMessage.includes('pdf_password_invalid')
        || lowerMessage.includes('invalid')
        || message.includes('密碼錯誤');
      set({
        jobStatus: isInvalid ? 'PASSWORD_INVALID' : 'PASSWORD_REQUIRED',
        errorMessage: isInvalid ? '密碼錯誤，請重新輸入' : message,
      });
    } finally {
      set({ isLoading: false });
    }
  },
}));
