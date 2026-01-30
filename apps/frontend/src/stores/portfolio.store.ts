import { create } from 'zustand';
import { message } from 'antd';
import { portfoliosApi } from '../api/portfolios.api';
import type { Position, PortfolioSummary, Trade } from '../api/portfolios.api';
import type { ApiResponse, PageResponse } from '../types/api';

interface PortfolioState {
  summary: PortfolioSummary | null;
  currentPortfolioId: string | null;
  positions: Position[];
  recentTrades: Trade[];
  trades: Trade[];
  tradesTotal: number;
  isLoading: boolean;
  error: string | null;

  initPortfolioId: () => Promise<string | null>;
  fetchPortfolioData: (portfolioId?: string) => Promise<void>;
  fetchPortfolioSummary: (portfolioId?: string) => Promise<void>;
  fetchRecentTrades: (portfolioId?: string) => Promise<void>;
  fetchTrades: (portfolioId?: string, page?: number, size?: number) => Promise<void>;
  addTrade: (trade: any) => Promise<void>;
  updateTrade: (tradeId: string, trade: any) => Promise<void>;
  deleteTrade: (tradeId: string) => Promise<void>;
}

export const usePortfolioStore = create<PortfolioState>((set, get) => ({
  summary: null,
  currentPortfolioId: null,
  positions: [],
  recentTrades: [],
  trades: [],
  tradesTotal: 0,
  isLoading: false,
  error: null,

  initPortfolioId: async () => {
    const state = get();
    if (state.currentPortfolioId) return state.currentPortfolioId;

    try {
      const res = await portfoliosApi.getPortfolios();
      const data = (res as unknown as ApiResponse<any>).data;
      // Handle both PageResponse (data.items) and List (data)
      const list = Array.isArray(data) ? data : data?.items;
      
      if (list && list.length > 0) {
        const pid = String(list[0].id); // Ensure string
        set({ currentPortfolioId: pid });
        return pid;
      }
    } catch (e) {
      console.error('Failed to init portfolio', e);
    }
    return null;
  },

  fetchPortfolioData: async (portfolioId?: string) => {
    const pid = portfolioId || await get().initPortfolioId();
    if (!pid) return;

    set({ isLoading: true, error: null });
    try {
      // Parallel fetch summary and positions
      const [summaryRes, positionsRes] = await Promise.all([
        portfoliosApi.getSummary(pid),
        portfoliosApi.getPositions(pid),
      ]);

      set({
        summary: (summaryRes as unknown as ApiResponse<PortfolioSummary>).data,
        positions: (positionsRes as unknown as ApiResponse<Position[]>).data,
      });
    } catch (err) {
      const errorMessage =
        err instanceof Error ? err.message : 'Failed to fetch portfolio data';
      set({ error: errorMessage });
    } finally {
      set({ isLoading: false });
    }
  },

  fetchPortfolioSummary: async (portfolioId?: string) => {
    const pid = portfolioId || await get().initPortfolioId();
    if (!pid) return;

    const currentSummary = get().summary;
    if (currentSummary && String(currentSummary.id) === pid) return;

    set({ isLoading: true, error: null });
    try {
      const summaryRes = await portfoliosApi.getSummary(pid);
      set({
        summary: (summaryRes as unknown as ApiResponse<PortfolioSummary>).data,
      });
    } catch (err) {
      const errorMessage =
        err instanceof Error ? err.message : 'Failed to fetch portfolio summary';
      set({ error: errorMessage });
    } finally {
      set({ isLoading: false });
    }
  },

  fetchRecentTrades: async (portfolioId?: string) => {
    const pid = portfolioId || await get().initPortfolioId();
    if (!pid) return;

    try {
      const res = await portfoliosApi.getTrades(pid, 1, 5); // Fetch top 5
      set({
        recentTrades: (res as unknown as PageResponse<Trade>).data.items,
      });
    } catch (err) {
      console.error('Failed to fetch recent trades', err);
    }
  },

  fetchTrades: async (portfolioId?: string, page = 1, size = 20) => {
    const pid = portfolioId || await get().initPortfolioId();
    if (!pid) return;

    set({ isLoading: true });
    try {
      const res = await portfoliosApi.getTrades(pid, page, size);
      const pageData = (res as unknown as PageResponse<Trade>).data;
      set({
        trades: pageData.items,
        tradesTotal: pageData.total,
      });
    } catch (err) {
      console.error('Failed to fetch trades', err);
    } finally {
      set({ isLoading: false });
    }
  },

  addTrade: async (trade: any) => {
    set({ isLoading: true, error: null });
    try {
      const portfolioId = await get().initPortfolioId();
      if (!portfolioId) throw new Error('No portfolio found');

      await portfoliosApi.addTrade(portfolioId, trade);
      await get().fetchPortfolioData(portfolioId);
    } catch (err) {
      const errorMessage =
        err instanceof Error ? err.message : 'Failed to add trade';
      set({ error: errorMessage });
      throw err;
    } finally {
      set({ isLoading: false });
    }
  },

  updateTrade: async (tradeId: string, trade: any) => {
    set({ isLoading: true, error: null });
    try {
      await portfoliosApi.updateTrade(tradeId, trade);
      message.success('交易已更新');
      const pid = get().currentPortfolioId;
      if (pid) {
        await get().fetchTrades(pid);
        await get().fetchPortfolioData(pid);
      }
    } catch (err) {
      message.error('更新失敗');
      throw err;
    } finally {
      set({ isLoading: false });
    }
  },

  deleteTrade: async (tradeId: string) => {
    set({ isLoading: true, error: null });
    try {
      await portfoliosApi.deleteTrade(tradeId);
      message.success('交易已刪除');
      const pid = get().currentPortfolioId;
      if (pid) {
        await get().fetchTrades(pid);
        await get().fetchPortfolioData(pid);
      }
    } catch (err) {
      message.error('刪除失敗');
    } finally {
      set({ isLoading: false });
    }
  },
}));


