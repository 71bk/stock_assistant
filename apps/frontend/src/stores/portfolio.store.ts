import { create } from 'zustand';
import { message, notification } from 'antd';
import { portfoliosApi } from '../api/portfolios.api';
import type { Position, PortfolioSummary, Trade } from '../api/portfolios.api';

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
  addTrade: (trade: Omit<Trade, 'tradeId'>) => Promise<void>;
  updateTrade: (tradeId: string, trade: Partial<Trade>) => Promise<void>;
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
      const list = await portfoliosApi.getPortfolios();

      if (Array.isArray(list) && list.length > 0) {
        const pid = String(list[0].id); // Ensure string
        set({ currentPortfolioId: pid });
        return pid;
      }
    } catch (e) {
      console.error('Failed to init portfolio', e);
    }

    notification.warning({
      message: '尚未建立投資組合',
      description: '您尚未建立投資組合，請先前往投資組合頁面建立。',
      duration: 5,
    });
    return null;
  },

  fetchPortfolioData: async (portfolioId?: string) => {
    if (get().isLoading) return;
    const pid = portfolioId || await get().initPortfolioId();
    if (!pid) return;

    set({ isLoading: true, error: null });
    try {
      // Use allSettled to isolate errors
      const results = await Promise.allSettled([
        portfoliosApi.getSummary(pid),
        portfoliosApi.getPositions(pid),
      ]);

      const summaryResult = results[0];
      const positionsResult = results[1];

      if (summaryResult.status === 'fulfilled') {
        set({ summary: summaryResult.value });
      } else {
        console.error('Failed to fetch summary', summaryResult.reason);
        message.error('無法載入投資組合摘要');
      }

      if (positionsResult.status === 'fulfilled') {
        set({ positions: positionsResult.value });
      } else {
        console.error('Failed to fetch positions', positionsResult.reason);
        message.error('無法載入持倉資料');
      }

      if (summaryResult.status === 'rejected' && positionsResult.status === 'rejected') {
        set({ error: '無法載入投資組合資料' });
      }
    } catch (err) {
      const errorMessage =
        err instanceof Error ? err.message : '發生未知錯誤';
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
      const summary = await portfoliosApi.getSummary(pid);
      set({
        summary,
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
      const res = await portfoliosApi.getTrades(pid, 1, 5);
      set({
        recentTrades: res.items,
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
      const pageData = await portfoliosApi.getTrades(pid, page, size);
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

  addTrade: async (trade: Omit<Trade, 'tradeId'>) => {
    if (get().isLoading) return;
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

  updateTrade: async (tradeId: string, trade: Partial<Trade>) => {
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
      console.error('Update trade failed', err);
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
    } catch (error) {
      console.error('Delete trade failed', error);
      message.error('刪除失敗');
    } finally {
      set({ isLoading: false });
    }
  },
}));


