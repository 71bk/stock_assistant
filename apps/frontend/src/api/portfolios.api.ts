import { http } from '../utils/http';

export interface Trade {
  tradeId: string;
  instrumentId: string;
  tradeDate: string;
  settlementDate?: string | null;
  side: string;
  quantity: string | number;
  price: string | number;
  currency: string;
  grossAmount?: string | number;
  fee?: string | number;
  tax?: string | number;
  netAmount?: string | number;
}

export interface Position {
  positionId: string;
  portfolioId: string;
  instrumentId: string;
  ticker: string;
  name: string;
  totalQuantity: number; // Backend: totalQuantity
  avgCostNative: number; // Backend: avgCostNative
  currency: string;
  // Optional fields (backend might not return yet)
  exchange?: string;
  market?: string;
  currentPrice?: number;
  currentValue?: number;
  unrealizedPnl?: number;
  unrealizedPnlPercent?: number;
  lastUpdated?: string;
}

export interface PortfolioSummary {
  id: string;
  name: string;
  baseCurrency: string;
  totalMarketValue: number;
  totalCost: number;
  totalPnl: number;
  totalPnlPercent: number;
}

export interface CreateTradeRequest {
  instrumentId: string;
  tradeDate: string;
  settlementDate?: string | null;
  side: string;
  quantity: string | number;
  price: string | number;
  currency: string;
  fee?: string | number;
  tax?: string | number;
}

export type UpdateTradeRequest = Partial<CreateTradeRequest>;

export const portfoliosApi = {
  getSummary: (id: string = 'default') =>
    http.get<PortfolioSummary>(`/portfolios/${id}`),
    
  getPositions: (id: string = 'default') =>
    http.get<Position[]>(`/portfolios/${id}/positions`),

  addTrade: (portfolioId: string, trade: CreateTradeRequest) =>
    http.post<{ tradeId: string }>(`/portfolios/${portfolioId}/trades`, trade),

  getTrades: (id: string = 'default', page = 1, size = 20) =>
    http.get<{ items: Trade[]; page: number; size: number; total: number }>(`/portfolios/${id}/trades`, { params: { page, size } }),

  getPortfolios: () =>
    http.get<PortfolioSummary[]>('/portfolios'),

  createPortfolio: (data: { name: string; baseCurrency: string }) =>
    http.post<{ id: string }>('/portfolios', data),

  updateTrade: (tradeId: string, trade: UpdateTradeRequest) =>
    http.patch<{ tradeId: string }>(`/trades/${tradeId}`, trade),

  deleteTrade: (tradeId: string) =>
    http.delete<void>(`/trades/${tradeId}`),
};