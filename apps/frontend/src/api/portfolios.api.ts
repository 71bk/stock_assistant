import { http } from "../utils/http";

export interface Trade {
  id: string;
  portfolio_id: string;
  instrument_id: string;
  trade_date: string;
  side: "BUY" | "SELL";
  quantity: string;
  price: string;
  currency: string;
  fee?: string;
  tax?: string;
}

export interface Position {
  portfolio_id: string;
  instrument_id: string;
  total_quantity: string;
  avg_cost_native: string;
  currency: string;
  updated_at: string;
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

/**
 * 新增交易
 */
export const createTrade = async (
  portfolioId: string,
  trade: Omit<Trade, "id" | "portfolio_id">
): Promise<Trade> => {
  const response = await http.post(`/portfolios/${portfolioId}/trades`, trade);
  return response.data.data;
};

/**
 * 獲取交易列表
 */
export const getTrades = async (
  portfolioId: string,
  page: number = 1,
  size: number = 20
): Promise<{ items: Trade[]; total: number }> => {
  const response = await http.get<ListResponse<Trade>>(
    `/portfolios/${portfolioId}/trades`,
    {
      params: { page, size },
    }
  );
  const { items, total } = response.data.data;
  return { items, total };
};

/**
 * 獲取持倉列表
 */
export const getPositions = async (
  portfolioId: string
): Promise<Position[]> => {
  const response = await http.get<ListResponse<Position>>(
    `/portfolios/${portfolioId}/positions`
  );
  return response.data.data.items;
};
