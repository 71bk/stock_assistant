import { http } from '../utils/http';
import type { PageData } from '../types/api';

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
  changePct?: string; // 兼容舊欄位
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

export interface EtfProfile {
  instrumentId: string;
  underlyingType: string;
  underlyingName: string;
  asOfDate: string;
}

export interface WarrantProfile {
  instrumentId: string;
  underlyingSymbol: string;
  expiryDate: string;
}

export interface TickerItem {
  symbol: string;
  name: string;
}

export interface TickersResponse {
  date: string;
  type: string;
  exchange?: string;
  market?: string;
  data: TickerItem[];
}

export const stocksApi = {
  search: (query: string) =>
    http.get<Instrument[]>('/instruments/search', { params: { q: query } }),

  getQuote: (symbolKey: string) =>
    http.get<Quote>('/stocks/quote', { params: { symbolKey } }),

  getCandles: (symbolKey: string, interval: string, from?: string, to?: string) =>
    http.get<Candle[]>('/stocks/candles', {
      params: { symbolKey, interval, from, to },
    }),

  getMarkets: () =>
    http.get<Array<{ code: string; name: string }>>('/stocks/markets'),

  getExchanges: (market?: string) =>
    http.get<Array<{ code: string; name: string; market: string }>>('/stocks/exchanges', { params: { market } }),

  getTickers: (params: { type: string; exchange?: string; market?: string }) =>
    http.get<TickersResponse>('/stocks/tickers', { params }),

  getInstruments: (page = 1, size = 20) =>
    http.get<PageData<Instrument>>('/instruments', { params: { page, size } }),

  getInstrumentDetail: (symbolKey: string) =>
    http.get<{ instrument: Instrument; etfProfile: EtfProfile | null; warrantProfile: WarrantProfile | null }>(`/instruments/${symbolKey}`),

  getInstrumentById: (instrumentId: string) =>
    http.get<Instrument>(`/stocks/instruments/${instrumentId}`),

  addInstrument: (instrument: Omit<Instrument, 'instrumentId' | 'symbolKey'>) =>
    http.post<Instrument>('/instruments', instrument),
};