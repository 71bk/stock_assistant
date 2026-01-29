import { http } from '../utils/http';
import type { ApiResponse } from '../types/api';

export interface Instrument {
  id: string;
  symbol: string;
  exchange: string;
  market: string;
  name: string;
  type: string;
  currency: string;
}

export interface Quote {
  instrumentId: string;
  symbolKey: string;
  price: string;
  change: string;
  changePct: string;
  tsUtc: string;
}

export const stocksApi = {
  search: (query: string) =>
    http.get<ApiResponse<Instrument[]>>('/instruments/search', { params: { q: query } }),

  getQuote: (symbolKey: string) =>
    http.get<ApiResponse<any>>('/stocks/quote', { params: { symbol_key: symbolKey } }),
};