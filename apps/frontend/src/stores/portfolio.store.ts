import { create } from 'zustand';
// import { portfoliosApi } from '../api/portfolios.api';
import type { Position, PortfolioSummary } from '../api/portfolios.api';

interface PortfolioState {
  summary: PortfolioSummary | null;
  positions: Position[];
  isLoading: boolean;
  error: string | null;
  
  fetchPortfolioData: (portfolioId?: string) => Promise<void>;
  fetchPortfolioSummary: (portfolioId?: string) => Promise<void>;
  addTrade: (trade: any) => Promise<void>;
}

// Mock Data for MVP visualization if API fails or is empty
const MOCK_POSITIONS: Position[] = [
  { instrumentId: '1', symbol: 'AAPL', exchange: 'NASDAQ', market: 'US', name: 'Apple Inc.', quantity: 150, avgCost: 145.50, currency: 'USD', currentPrice: 185.50, currentValue: 27825, unrealizedPnl: 6000, unrealizedPnlPercent: 27.5, lastUpdated: new Date().toISOString() },
  { instrumentId: '2', symbol: '2330', exchange: 'TWSE', market: 'TW', name: '台積電', quantity: 2000, avgCost: 500, currency: 'TWD', currentPrice: 580, currentValue: 1160000, unrealizedPnl: 160000, unrealizedPnlPercent: 16.0, lastUpdated: new Date().toISOString() },
];

export const usePortfolioStore = create<PortfolioState>((set, get) => ({
  summary: null,
  positions: [],
  isLoading: false,
  error: null,

  fetchPortfolioData: async () => {
    // console.log('Fetching portfolio data for:', _portfolioId);
    set({ isLoading: true, error: null });
    try {
      // Parallel fetch summary and positions
      // const [summaryRes, positionsRes] = await Promise.all([
      //   portfoliosApi.getSummary(_portfolioId),
      //   portfoliosApi.getPositions(_portfolioId)
      // ]);
      
      // Using Mock for now since backend is not ready
      await new Promise(resolve => setTimeout(resolve, 800)); // Simulate latency
      set({ 
        summary: { id: 'default', name: 'My Portfolio', baseCurrency: 'TWD', totalMarketValue: 2500000, totalCost: 2000000, totalPnl: 500000, totalPnlPercent: 25 },
        positions: MOCK_POSITIONS 
      });

    } catch (err) {
      const errorMessage = err instanceof Error ? err.message : 'Failed to fetch portfolio data';
      set({ error: errorMessage });
    } finally {
      set({ isLoading: false });
    }
  },

  fetchPortfolioSummary: async () => {
     // console.log('Fetching portfolio summary for:', _portfolioId);
     // If we already have summary from fetchPortfolioData, we might skip or force refresh
     // For now, reuse the same mock logic but only for summary
     const currentSummary = get().summary;
     if(currentSummary) return;

     set({ isLoading: true, error: null });
     try {
        await new Promise(resolve => setTimeout(resolve, 500));
        set({ 
          summary: { id: 'default', name: 'My Portfolio', baseCurrency: 'TWD', totalMarketValue: 2500000, totalCost: 2000000, totalPnl: 500000, totalPnlPercent: 25 }
        });
     } catch (err) {
        const errorMessage = err instanceof Error ? err.message : 'Failed to fetch portfolio summary';
        set({ error: errorMessage });
     } finally {
        set({ isLoading: false });
     }
  }
}));


