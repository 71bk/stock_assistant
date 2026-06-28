import { create } from 'zustand';
import { message } from 'antd';
import { portfoliosApi } from '../api/portfolios.api';
import type { Position, PortfolioSummary, Trade, PortfolioValuation } from '../api/portfolios.api';
import { logger } from '../utils/logger';

// Shared in-flight portfolio lookup so the many data fetches a page triggers on
// load don't each hit the API.
let initInFlight: Promise<string | null> | null = null;
// Bridges the global "create portfolio" modal back to whoever awaited
// requirePortfolio(), so the original action (e.g. OCR upload) can resume.
let pendingPortfolioResolve: ((id: string | null) => void) | null = null;

// Persist the selected portfolio so a switch survives reloads. Validated against
// the live list on init, so a stale id (or another user's) safely falls back.
const PORTFOLIO_ID_KEY = 'current_portfolio_id';
function readPersistedPortfolioId(): string | null {
  try { return window.localStorage.getItem(PORTFOLIO_ID_KEY); } catch { return null; }
}
function persistPortfolioId(id: string): void {
  try { window.localStorage.setItem(PORTFOLIO_ID_KEY, id); } catch { /* ignore */ }
}

interface PortfolioState {
  summary: PortfolioSummary | null;
  currentPortfolioId: string | null;
  portfolios: PortfolioSummary[];
  positions: Position[];
  recentTrades: Trade[];
  valuations: PortfolioValuation[];
  trades: Trade[];
  tradesTotal: number;
  isLoading: boolean;
  error: string | null;

  noPortfolio: boolean;
  createModalOpen: boolean;
  initPortfolioId: () => Promise<string | null>;
  requirePortfolio: () => Promise<string | null>;
  setCurrentPortfolio: (id: string) => void;
  openCreatePortfolio: () => void;
  createPortfolio: (name: string, baseCurrency: string) => Promise<string>;
  cancelCreatePortfolio: () => void;
  fetchPortfolioData: (portfolioId?: string) => Promise<void>;
  fetchPortfolioValuations: (portfolioId?: string, from?: string, to?: string) => Promise<void>;
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
  portfolios: [],
  positions: [],
  recentTrades: [],
  valuations: [],
  trades: [],
  tradesTotal: 0,
  isLoading: false,
  error: null,
  noPortfolio: false,
  createModalOpen: false,

  initPortfolioId: async () => {
    const state = get();
    if (state.currentPortfolioId) return state.currentPortfolioId;
    // Concurrent callers (dashboard fires several fetches at once) share one lookup.
    if (initInFlight) return initInFlight;

    initInFlight = (async () => {
      try {
        const list = await portfoliosApi.getPortfolios();
        const portfolios = Array.isArray(list) ? list : [];
        set({ portfolios });

        if (portfolios.length > 0) {
          // Prefer the user's last-selected portfolio if it still exists.
          const persisted = readPersistedPortfolioId();
          const pid = persisted && portfolios.some((p) => String(p.id) === persisted)
            ? persisted
            : String(portfolios[0].id);
          set({ currentPortfolioId: pid, noPortfolio: false });
          persistPortfolioId(pid);
          return pid;
        }
        // Empty list = the user genuinely has no portfolio yet. The UI surfaces
        // this via an empty-state / create modal instead of a toast.
        set({ noPortfolio: true });
      } catch (e) {
        logger.error('Failed to init portfolio', e);
      }
      return null;
    })();

    try {
      return await initInFlight;
    } finally {
      initInFlight = null;
    }
  },

  // Returns a usable portfolio id, opening the global create-portfolio modal and
  // waiting for the user when none exists. Lets callers (OCR upload, add trade)
  // transparently resume once the first portfolio is created.
  requirePortfolio: async () => {
    const existing = await get().initPortfolioId();
    if (existing) return existing;
    return new Promise<string | null>((resolve) => {
      pendingPortfolioResolve = resolve;
      set({ createModalOpen: true });
    });
  },

  // Switch the active portfolio. Pages re-fetch via their effects on
  // currentPortfolioId; the choice is persisted so it survives reloads.
  setCurrentPortfolio: (id: string) => {
    const pid = String(id);
    if (pid === get().currentPortfolioId) return;
    persistPortfolioId(pid);
    set({ currentPortfolioId: pid });
  },

  openCreatePortfolio: () => set({ createModalOpen: true }),

  createPortfolio: async (name: string, baseCurrency: string) => {
    const res = await portfoliosApi.createPortfolio({ name, baseCurrency });
    const pid = String(res.id);
    persistPortfolioId(pid);

    // Refresh the list so the switcher includes the new portfolio.
    let portfolios = get().portfolios;
    try {
      const list = await portfoliosApi.getPortfolios();
      if (Array.isArray(list)) portfolios = list;
    } catch { /* keep existing list; the new id is still set below */ }

    set({ currentPortfolioId: pid, noPortfolio: false, createModalOpen: false, portfolios });
    if (pendingPortfolioResolve) {
      pendingPortfolioResolve(pid);
      pendingPortfolioResolve = null;
    }
    return pid;
  },

  cancelCreatePortfolio: () => {
    set({ createModalOpen: false });
    if (pendingPortfolioResolve) {
      pendingPortfolioResolve(null);
      pendingPortfolioResolve = null;
    }
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
        logger.error('Failed to fetch summary', summaryResult.reason);
        message.error('無法載入投資組合摘要');
      }

      if (positionsResult.status === 'fulfilled') {
        set({ positions: positionsResult.value });
      } else {
        logger.error('Failed to fetch positions', positionsResult.reason);
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
      logger.error('Failed to fetch recent trades', err);
    }
  },

  fetchPortfolioValuations: async (portfolioId?: string, from?: string, to?: string) => {
    const pid = portfolioId || await get().initPortfolioId();
    if (!pid) return;

    try {
      const res = await portfoliosApi.getValuations(pid, from, to);
      set({
        valuations: res,
      });
    } catch (err) {
      logger.error('Failed to fetch valuations', err);
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
      logger.error('Failed to fetch trades', err);
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
      logger.error('Update trade failed', err);
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
      logger.error('Delete trade failed', error);
      message.error('刪除失敗');
    } finally {
      set({ isLoading: false });
    }
  },
}));

