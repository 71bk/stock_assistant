import { http } from "../utils/http";

export interface Instrument {
  id: string;
  symbol_key: string;
  ticker: string;
  name_zh: string;
  name_en: string;
  market: string;
  exchange: string;
  currency: string;
}

export interface Quote {
  instrument_id: string;
  symbol_key: string;
  price: string;
  change: string;
  change_pct: string;
  ts_utc: string;
}

export interface Candle {
  ts_utc: string;
  open: string;
  high: string;
  low: string;
  close: string;
  volume: string;
}

interface ListResponse<T> {
  success: boolean;
  data: {
    items: T[];
    page: number;
    size: number;
    total: number;
  };
  error: null;
  traceId: string;
}

interface DataResponse<T> {
  success: boolean;
  data: T;
  error: null;
  traceId: string;
}

/**
 * 搜尋股票
 */
export const searchInstruments = async (
  query: string,
  market?: string,
  limit: number = 20
): Promise<Instrument[]> => {
  const response = await http.get<ListResponse<Instrument>>(
    "/stocks/instruments",
    {
      params: {
        q: query,
        market,
        limit,
      },
    }
  );
  return response.data.data.items;
};

/**
 * 獲取即時報價
 */
export const getQuote = async (
  instrumentId: string
): Promise<Quote> => {
  const response = await http.get<DataResponse<Quote>>("/stocks/quote", {
    params: { instrument_id: instrumentId },
  });
  return response.data.data;
};

/**
 * 獲取 K 線資料
 */
export const getCandles = async (
  instrumentId: string,
  interval: "1d" | "1w" | "1m" | "1y" = "1d",
  from?: string,
  to?: string
): Promise<Candle[]> => {
  const response = await http.get<ListResponse<Candle>>("/stocks/candles", {
    params: {
      instrument_id: instrumentId,
      interval,
      from,
      to,
    },
  });
  return response.data.data.items;
};
