/**
 * 領域型別（Domain Models）
 */

export interface Market {
  id: string;
  code: string;
  name: string;
  timezone: string;
  default_currency: string;
}

export interface Exchange {
  id: string;
  market_id: string;
  mic: string;
  code?: string;
  name: string;
}

export interface Instrument {
  id: string;
  market_id: string;
  exchange_id: string;
  ticker: string;
  symbol_key: string;
  name_zh: string;
  name_en: string;
  currency: string;
  status: "ACTIVE" | "DELISTED" | "SUSPENDED";
}

export interface Portfolio {
  id: string;
  user_id: string;
  name: string;
  base_currency: string;
}

export interface Trade {
  id: string;
  user_id: string;
  portfolio_id: string;
  instrument_id: string;
  trade_date: string;
  side: "BUY" | "SELL";
  quantity: string;
  price: string;
  currency: string;
  gross_amount?: string;
  fee?: string;
  tax?: string;
  net_amount?: string;
}

export interface Position {
  portfolio_id: string;
  instrument_id: string;
  total_quantity: string;
  avg_cost_native: string;
  currency: string;
}
