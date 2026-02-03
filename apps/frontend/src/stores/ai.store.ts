import { create } from 'zustand';
import { aiApi } from '../api/ai.api';
import type { AiReport } from '../api/ai.api';
import type { PageResponse } from '../types/api';
import { message } from 'antd';

interface AiState {
  reports: AiReport[];
  totalReports: number;
  isLoading: boolean;
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
  isAnalyzing: false,
  analysisStream: '',

  fetchReports: async (page = 1, size = 20) => {
    set({ isLoading: true });
    try {
      const res = await aiApi.getReports(page, size);
      const data = (res as unknown as PageResponse<AiReport>).data;
      set({ reports: data.items, totalReports: data.total });
    } catch (e) {
      console.error('Failed to fetch reports', e);
    } finally {
      set({ isLoading: false });
    }
  },

  resetAnalysis: () => set({ analysisStream: '', isAnalyzing: false }),

  startAnalysis: async (params) => {
    set({ isAnalyzing: true, analysisStream: '' });
    
    try {
      // Since it's a POST SSE, we use fetch instead of axios/http for easier streaming
      // Note: We need to pass credentials (cookies) if using fetch with the same domain
      const response = await fetch('/api/ai/analysis/stream', {
        method: 'POST',
        credentials: 'include', // Ensure cookies are sent
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(params),
      });

      if (!response.ok) throw new Error('Analysis failed');
      
      const reader = response.body?.getReader();
      const decoder = new TextDecoder();

      if (!reader) throw new Error('No reader available');

      let done = false;
      while (!done) {
        const { value, done: readerDone } = await reader.read();
        done = readerDone;
        const chunk = decoder.decode(value, { stream: !done });
        
        // SSE parsing logic (simplified for "event: delta / data: ...")
        const lines = chunk.split('\n');
        for (const line of lines) {
          if (line.startsWith('data: ')) {
            try {
              const dataStr = line.replace('data: ', '').trim();
              if (dataStr === '[DONE]') break;
              
              const json = JSON.parse(dataStr);
              if (json.text) {
                set((state) => ({ analysisStream: state.analysisStream + json.text }));
              }
            } catch (err) {
              // Ignore partial JSON or meta events for now
              console.debug('Partial JSON or non-JSON chunk', err);
            }
          }
        }
      }
    } catch (e) {
      console.error('SSE Error', e);
      message.error('分析出錯，請稍後再試');
    } finally {
      set({ isAnalyzing: false });
      get().fetchReports(); // Refresh history
    }
  },
}));
