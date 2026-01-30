import { create } from 'zustand';
import { ocrApi } from '../api/ocr.api';
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

  uploadFile: (file: File) => Promise<void>;
  reprocessJob: () => Promise<void>;
  pollJob: (jobId: string) => void;
  reset: () => void;
  updateDraftTrade: (id: string, updates: Partial<DraftTrade>) => Promise<void>;
  deleteDraftTrade: (id: string) => void;
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

  deleteDraftTrade: (id) => {
    set((state) => ({
      draftTrades: state.draftTrades.filter((t) => t.draftId !== id),
    }));
  },

  confirmTrades: async (selectedIds) => {
    const { activeJobId, statementId, draftTrades } = get();
    const tradesToImport = draftTrades.filter((t) => selectedIds.includes(t.draftId));

    if (!statementId || !activeJobId) return;

    try {
      await ocrApi.confirmImport(activeJobId, statementId, tradesToImport);
      set({ currentStep: 2 }); // Success Step
    } catch (e) {
      message.error('Import confirmation failed');
    }
  },
}));
