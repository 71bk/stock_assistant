import { http } from '../utils/http';
import type { ApiResponse, PageResponse } from '../types/api';

export interface Instrument {
  instrumentId: string;
  symbolKey: string;
  ticker: string;
  exchange: string;
  market: string;
  nameZh: string;
  nameEn: string;
  assetType: string;
  currency: string;
}

export interface Quote {
  instrumentId: string;
  symbolKey: string;
  price: string;
  change: string;
  changePercent: string;
  timestamp: string;
}

export interface Candle {
  ts: string;
  o: number;
  h: number;
  l: number;
  c: number;
  v: number;
}

export const stocksApi = {
  search: (query: string) =>
    http.get<ApiResponse<Instrument[]>>('/instruments/search', { params: { q: query } }),

  getQuote: (symbolKey: string) =>
    http.get<ApiResponse<any>>('/stocks/quote', { params: { symbolKey } }),

  getCandles: (symbolKey: string, interval: string, from?: string, to?: string) =>
    http.get<ApiResponse<Candle[]>>('/stocks/candles', {
      params: { symbolKey, interval, from, to },
    }),

  getMarkets: () =>
    http.get<ApiResponse<any[]>>('/stocks/markets'),

  getExchanges: (market?: string) =>
    http.get<ApiResponse<any[]>>('/stocks/exchanges', { params: { market } }),

  getTickers: (params: any) =>
    http.get<ApiResponse<any>>('/stocks/tickers', { params }),

  getInstruments: (page = 1, size = 20) =>
    http.get<ApiResponse<PageResponse<Instrument>>>('/instruments', { params: { page, size } }),

  getInstrumentDetail: (symbolKey: string) =>
    http.get<ApiResponse<{ instrument: Instrument; etfProfile: any }>>(`/instruments/${symbolKey}`),

  getInstrumentById: (instrumentId: string) =>
    http.get<ApiResponse<Instrument>>(`/stocks/instruments/${instrumentId}`),
};