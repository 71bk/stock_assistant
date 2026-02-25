import { create } from 'zustand';
import { aiApi } from '../api/ai.api';
import type { AiReportSummary } from '../api/ai.api';
import { message } from 'antd';
import { fetchSseWithRetry } from '../utils/sse';
import { env } from '../app/env';

interface AiState {
  reports: AiReportSummary[];
  totalReports: number;
  isLoading: boolean;
  error: string | null;
  isAnalyzing: boolean;
  analysisStream: string;
  
  fetchReports: (page?: number, size?: number) => Promise<void>;
  startAnalysis: (params: { portfolioId?: string; instrumentId?: string; reportType: string; prompt: string }) => Promise<void>;
  resetAnalysis: () => void;
}

export const useAiStore = create<AiState>((set, get) => ({
  reports: [],
  totalReports: 0,
  isLoading: false,
  error: null,
  isAnalyzing: false,
  analysisStream: '',

  fetchReports: async (page = 1, size = 20) => {
    set({ isLoading: true, error: null });
    try {
      const res = await aiApi.getReports(page, size);
      set({ reports: res.items, totalReports: res.total });
    } catch (e) {
      console.error('Failed to fetch reports', e);
      set({ error: e instanceof Error ? e.message : '無法載入報告' });
    } finally {
      set({ isLoading: false });
    }
  },

  resetAnalysis: () => set({ analysisStream: '', isAnalyzing: false }),

  startAnalysis: async (params) => {
    set({ isAnalyzing: true, analysisStream: '' });
    
    try {
      // Use the shared SSE utility for robust parsing
      const apiBaseUrl = env.API_BASE_URL.endsWith('/') ? env.API_BASE_URL.slice(0, -1) : env.API_BASE_URL;
      await fetchSseWithRetry({
        url: `${apiBaseUrl}/ai/analysis/stream`,
        body: params,
        onDelta: (text) => {
          set((state) => ({ analysisStream: state.analysisStream + text }));
        },
        onError: (err) => {
          console.error('SSE Error', err);
          message.error('分析出錯，請稍後再試');
        },
        onDone: () => {
          set({ isAnalyzing: false });
          get().fetchReports();
        }
      });
    } catch (e) {
      console.error('Analysis start failed', e);
      message.error('無法開始分析');
      set({ isAnalyzing: false });
    }
  },
}));
