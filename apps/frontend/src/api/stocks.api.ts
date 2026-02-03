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
  open: string;
  high: string;
  low: string;
  previousClose: string;
  volume: number;
  change: string;
  changePercent: string;
  timestamp: string;
}

export interface Candle {
  timestamp: string;
  open: string;
  high: string;
  low: string;
  close: string;
  volume: number;
}

export const stocksApi = {
  search: (query: string) =>
    http.get<ApiResponse<Instrument[]>>('/instruments/search', { params: { q: query } }),

  getQuote: (symbolKey: string) =>
    http.get<ApiResponse<Quote>>('/stocks/quote', { params: { symbolKey } }),

  getCandles: (symbolKey: string, interval: string, from?: string, to?: string) =>
    http.get<ApiResponse<Candle[]>>('/stocks/candles', {
      params: { symbolKey, interval, from, to },
    }),

  getMarkets: () =>
    http.get<ApiResponse<Array<{ code: string; name: string }>>>('/stocks/markets'),

  getExchanges: (market?: string) =>
    http.get<ApiResponse<Array<{ code: string; name: string; market: string }>>>('/stocks/exchanges', { params: { market } }),

  getTickers: (params: { type: string; exchange?: string; market?: string }) =>
    http.get<ApiResponse<any>>('/stocks/tickers', { params }),

  getInstruments: (page = 1, size = 20) =>
    http.get<ApiResponse<PageResponse<Instrument>>>('/instruments', { params: { page, size } }),

  getInstrumentDetail: (symbolKey: string) =>
    http.get<ApiResponse<{ instrument: Instrument; etfProfile: any }>>(`/instruments/${symbolKey}`),

  getInstrumentById: (instrumentId: string) =>
    http.get<ApiResponse<Instrument>>(`/stocks/instruments/${instrumentId}`),
};