import { http } from '../utils/http';
import type { ApiResponse, PageResponse } from '../types/api';

export interface Trade {
  tradeId: string;
  instrumentId: string;
  tradeDate: string;
  side: string;
  quantity: number;
  price: number;
  currency: string;
  grossAmount?: number;
  fee?: number;
  tax?: number;
  netAmount?: number;
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

export const portfoliosApi = {
  getSummary: (id: string = 'default') =>
    http.get<ApiResponse<PortfolioSummary>>(`/portfolios/${id}`),
    
  getPositions: (id: string = 'default') =>
    http.get<ApiResponse<Position[]>>(`/portfolios/${id}/positions`),

  addTrade: (portfolioId: string, trade: any) =>
    http.post<ApiResponse<{ trade_id: string }>>(`/portfolios/${portfolioId}/trades`, trade),

  getTrades: (id: string = 'default', page = 1, size = 20) =>
    http.get<PageResponse<Trade>>(`/portfolios/${id}/trades`, { params: { page, size } }),

  getPortfolios: () =>
    http.get<ApiResponse<PageResponse<PortfolioSummary>>>('/portfolios'),

  createPortfolio: (data: { name: string; baseCurrency: string }) =>
    http.post<ApiResponse<{ id: string }>>('/portfolios', data),

  updateTrade: (tradeId: string, trade: any) =>
    http.patch<ApiResponse<{ tradeId: string }>>(`/trades/${tradeId}`, trade),

  deleteTrade: (tradeId: string) =>
    http.delete<ApiResponse<void>>(`/trades/${tradeId}`),
};