import { http } from '../utils/http';
import type { ApiResponse } from '../types/api';

export interface Position {
  instrumentId: string;
  symbol: string;
  exchange: string;
  market: string;
  name: string;
  quantity: number;
  avgCost: number;
  currency: string;
  currentPrice: number;
  currentValue: number;
  unrealizedPnl: number;
  unrealizedPnlPercent: number;
  lastUpdated: string; // UTC ISO
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
};