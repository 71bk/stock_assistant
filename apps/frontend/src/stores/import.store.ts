import { create } from 'zustand';
import { ocrApi } from '../api/ocr.api';
import type { DraftTrade, OcrJob } from '../api/ocr.api';
import { message } from 'antd';

interface ImportState {
  currentStep: number;
  activeJobId: string | null;
  jobStatus: OcrJob['status'] | null;
  progress: number;
  draftTrades: DraftTrade[];
  statementId: string | null;
  isPolling: boolean;

  uploadFile: (file: File) => Promise<void>;
  reset: () => void;
  updateDraftTrade: (id: string, updates: Partial<DraftTrade>) => void;
  deleteDraftTrade: (id: string) => void;
  confirmTrades: (selectedIds: string[]) => Promise<void>;
  setStep: (step: number) => void;
}

// Mock Draft Data Generator
const mockDraftTrades = (): DraftTrade[] => [
  { id: '1', symbol: 'AAPL', tradeDate: '2026-01-05', side: 'BUY', quantity: 10, price: 180.50, currency: 'USD', fee: 1.5, tax: 0, confidence: 0.95, warnings: [], status: 'VALID' },
  { id: '2', symbol: 'TSLA', tradeDate: '2026-01-05', side: 'SELL', quantity: 5, price: 235.00, currency: 'USD', fee: 1.5, tax: 0, confidence: 0.8, warnings: ['Check date format'], status: 'WARNING' },
  { id: '3', symbol: 'UNKNOWN', tradeDate: '2026-01-05', side: 'BUY', quantity: 1000, price: 0, currency: 'TWD', fee: 0, tax: 0, confidence: 0.4, warnings: ['Symbol not found', 'Price is zero'], status: 'ERROR' },
];

export const useImportStore = create<ImportState>((set, get) => ({
  currentStep: 0,
  activeJobId: null,
  jobStatus: null,
  progress: 0,
  draftTrades: [],
  statementId: null,
  isPolling: false,

  setStep: (step) => set({ currentStep: step }),

  reset: () => set({ currentStep: 0, activeJobId: null, jobStatus: null, progress: 0, draftTrades: [], statementId: null }),

  uploadFile: async (file) => {
    try {
      set({ currentStep: 1, progress: 0, jobStatus: 'QUEUED' }); // Move to processing step

      // Real API:
      // const res = await ocrApi.upload(file);
      // const jobId = res.data.job_id;
      // set({ activeJobId: jobId, isPolling: true });
      // get().pollJob(jobId);

      // Mock Flow:
      set({ activeJobId: 'mock-job-123', jobStatus: 'RUNNING', isPolling: true });

      // Simulate Progress
      let p = 0;
      const interval = setInterval(() => {
        p += 20;
        set({ progress: p });
        if (p >= 100) {
          clearInterval(interval);
          set({
            jobStatus: 'COMPLETED',
            draftTrades: mockDraftTrades(),
            statementId: 'stmt-123',
            isPolling: false
          });
          message.success('OCR Processing Complete');
        }
      }, 500);

    } catch (e) {
      set({ jobStatus: 'FAILED', isPolling: false });
      message.error('Upload failed');
    }
  },

  updateDraftTrade: (id, updates) => {
    set((state) => ({
      draftTrades: state.draftTrades.map((t) =>
        t.id === id ? { ...t, ...updates, status: 'VALID' } : t // Assume valid after manual edit
      ),
    }));
  },

  deleteDraftTrade: (id) => {
    set((state) => ({
      draftTrades: state.draftTrades.filter((t) => t.id !== id),
    }));
  },

  confirmTrades: async (selectedIds) => {
    const { activeJobId, statementId, draftTrades } = get();
    const tradesToImport = draftTrades.filter((t) => selectedIds.includes(t.id));

    if (!statementId || !activeJobId) return;

    try {
      // await ocrApi.confirmImport(activeJobId, statementId, tradesToImport);
      await new Promise((r) => setTimeout(r, 1000)); // Mock API
      set({ currentStep: 2 }); // Success Step
    } catch (e) {
      message.error('Import confirmation failed');
    }
  },
}));
