import { create } from "zustand";
import { Instrument, Quote } from "@/api/stocks.api";

interface StocksState {
  instruments: Instrument[];
  selectedInstrument: Instrument | null;
  quotes: Record<string, Quote>;
  isLoading: boolean;

  // Actions
  setInstruments: (instruments: Instrument[]) => void;
  setSelectedInstrument: (instrument: Instrument | null) => void;
  setQuote: (instrumentId: string, quote: Quote) => void;
  setLoading: (loading: boolean) => void;
}

/**
 * 股票狀態 store
 * 管理報價、列表、查詢條件
 */
export const useStocksStore = create<StocksState>((set) => ({
  instruments: [],
  selectedInstrument: null,
  quotes: {},
  isLoading: false,

  setInstruments: (instruments) =>
    set({
      instruments,
    }),

  setSelectedInstrument: (instrument) =>
    set({
      selectedInstrument: instrument,
    }),

  setQuote: (instrumentId, quote) =>
    set((state) => ({
      quotes: {
        ...state.quotes,
        [instrumentId]: quote,
      },
    })),

  setLoading: (isLoading) =>
    set({
      isLoading,
    }),
}));
