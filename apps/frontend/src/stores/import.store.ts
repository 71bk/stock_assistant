import { create } from 'zustand';
import { ocrApi } from '../api/ocr.api';
import { stocksApi } from '../api/stocks.api';
import type { DraftTrade, OcrJob } from '../api/ocr.api';
import type { ApiResponse } from '../types/api';
import { message } from 'antd';
import { usePortfolioStore } from './portfolio.store';

interface ImportState {
  currentStep: number;
  activeJobId: string | null;
  fileId: string | null;
  jobStatus: OcrJob['status'] | null;
  progress: number;
  draftTrades: DraftTrade[];
  statementId: string | null;
  isPolling: boolean;
  isLoading: boolean;

  uploadFile: (file: File) => Promise<void>;
  reprocessJob: () => Promise<void>;
  pollJob: (jobId: string) => void;
  reset: () => void;
  updateDraftTrade: (id: string, updates: Partial<DraftTrade>) => Promise<void>;
  deleteDraftTrade: (id: string) => Promise<void>;
  confirmTrades: (selectedIds: string[]) => Promise<void>;
  setStep: (step: number) => void;
}

export const useImportStore = create<ImportState>((set, get) => ({
  currentStep: 0,
  activeJobId: null,
  fileId: null,
  jobStatus: null,
  progress: 0,
  draftTrades: [],
  statementId: null,
  isPolling: false,
  isLoading: false,

  setStep: (step) => set({ currentStep: step }),

  reset: () =>
    set({
      currentStep: 0,
      activeJobId: null,
      fileId: null,
      jobStatus: null,
      progress: 0,
      draftTrades: [],
      statementId: null,
    }),

  uploadFile: async (file) => {
    try {
      set({ currentStep: 1, progress: 0, jobStatus: 'QUEUED' });

      const portfolioId = await usePortfolioStore.getState().initPortfolioId();
      if (!portfolioId) throw new Error('No portfolio found');

      const fileId = await ocrApi.uploadFileOnly(file);
      set({ fileId });

      const res = await ocrApi.createOcrJob(fileId, portfolioId);
      // @ts-ignore
      const jobId = res.data.jobId;
      set({ activeJobId: jobId, isPolling: true });
      get().pollJob(jobId);
    } catch (e) {
      set({ jobStatus: 'FAILED', isPolling: false });
      message.error('Upload failed');
    }
  },

  reprocessJob: async () => {
    const { fileId } = get();
    if (!fileId) return;

    try {
      const portfolioId = await usePortfolioStore.getState().initPortfolioId();
      if (!portfolioId) return;

      set({ jobStatus: 'QUEUED', progress: 0, draftTrades: [], activeJobId: null, isPolling: true });

      const res = await ocrApi.createOcrJob(fileId, portfolioId, true);
      // @ts-ignore
      const jobId = res.data.jobId;
      set({ activeJobId: jobId });
      get().pollJob(jobId);
    } catch (e) {
      set({ jobStatus: 'FAILED', isPolling: false });
      message.error('Reprocess failed');
    }
  },

  pollJob: (jobId: string) => {
    const interval = setInterval(async () => {
      try {
        const res = await ocrApi.getJob(jobId);
        const job = (res as unknown as ApiResponse<OcrJob>).data;

        set({ jobStatus: job.status, progress: job.progress });

        if (job.status === 'DONE' || job.status === 'FAILED') {
          clearInterval(interval);
          set({ isPolling: false });
          if (job.status === 'DONE') {
            try {
              const draftsRes = await ocrApi.getDrafts(jobId);
              // @ts-ignore
              const trades = draftsRes.data.items;
              set({
                draftTrades: trades,
                statementId: job.statementId,
              });
              message.success('OCR Processing Complete');
            } catch (err) {
              console.error('Failed to fetch drafts', err);
              message.error('Failed to load drafts');
            }
          }
        }
      } catch (e) {
        clearInterval(interval);
        set({ isPolling: false, jobStatus: 'FAILED' });
      }
    }, 2000);
  },

  updateDraftTrade: async (id, updates) => {
    try {
      await ocrApi.updateDraft(id, updates);
      set((state) => ({
        draftTrades: state.draftTrades.map((t) =>
          t.draftId === id ? { ...t, ...updates, status: 'VALID' } : t
        ),
      }));
    } catch (e) {
      message.error('Failed to update draft');
    }
  },

  deleteDraftTrade: async (id) => {
    try {
      await ocrApi.deleteDraft(id);
      set((state) => ({
        draftTrades: state.draftTrades.filter((t) => t.draftId !== id),
      }));
      message.success('草稿已刪除');
    } catch (e) {
      message.error('刪除草稿失敗');
    }
  },

  confirmTrades: async (selectedIds) => {
    const { activeJobId, statementId, draftTrades } = get();
    const tradesToImport = draftTrades.filter((t) => selectedIds.includes(t.draftId));

    if (!statementId || !activeJobId) return;

    set({ isLoading: true });
    try {
      // Step 1: Ensure all selected trades have instrumentId
      for (const trade of tradesToImport) {
        if (!trade.instrumentId) {
          const searchRes = await stocksApi.search(trade.rawTicker);
          const instruments = searchRes.data as any;
          if (instruments && instruments.length > 0) {
            const first = instruments[0];
            const instId = first.instrumentId;
            await ocrApi.updateDraft(trade.draftId, { instrumentId: instId });
            trade.instrumentId = instId;
          }
        }
      }

      // Step 2: Call confirm with draftIds (removed statementId parameter)
      const result = await ocrApi.confirmImport(activeJobId, selectedIds);
      const { importedCount, errors } = result.data as any;

      // Step 3: Handle errors if any
      if (errors && errors.length > 0) {
        // Mark drafts with errors
        const errorMap = new Map(errors.map((e: any) => [e.draftId, e.reason]));
        set((state) => ({
          draftTrades: state.draftTrades.map((t) => {
            const errorReason = errorMap.get(t.draftId);
            if (errorReason) {
              return {
                ...t,
                status: 'ERROR' as const,
                errors: [...(t.errors || []), errorReason],
              };
            }
            // Remove successfully imported drafts
            if (selectedIds.includes(t.draftId) && !errorMap.has(t.draftId)) {
              return null;
            }
            return t;
          }).filter(Boolean) as DraftTrade[],
        }));

        if (importedCount > 0) {
          message.success(`已成功匯入 ${importedCount} 筆交易`);
        }
        if (errors.length > 0) {
          message.warning(`${errors.length} 筆交易匯入失敗，請查看錯誤訊息`);
        }
      } else {
        // All successful - remove imported drafts from local state
        const remainingDrafts = draftTrades.filter((t) => !selectedIds.includes(t.draftId));
        set({ draftTrades: remainingDrafts });

        // If no drafts left, go to success step
        if (remainingDrafts.length === 0) {
          set({ currentStep: 2 });
        } else {
          message.success(`已成功匯入 ${importedCount} 筆交易`);
        }
      }
    } catch (e) {
      console.error('Import confirmation failed', e);
      message.error('匯入確認失敗，請確保已選取項目的標的代號正確');
    } finally {
      set({ isLoading: false });
    }
  },
}));
