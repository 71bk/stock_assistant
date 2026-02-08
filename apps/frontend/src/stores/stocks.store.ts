import { create } from "zustand";
import { stocksApi } from "../api/stocks.api";
import type { Instrument, Quote } from "../api/stocks.api";

interface StocksState {
  instruments: Instrument[];
  selectedInstrument: Instrument | null;
  quotes: Record<string, Quote>;
  isLoading: boolean;

  // Actions
  setInstruments: (instruments: Instrument[]) => void;
  setSelectedInstrument: (instrument: Instrument | null) => void;
  setQuote: (symbolKey: string, quote: Quote) => void;
  fetchQuote: (symbolKey: string) => Promise<void>;
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

  setQuote: (symbolKey, quote) =>
    set((state) => ({
      quotes: {
        ...state.quotes,
        [symbolKey]: quote,
      },
    })),

  fetchQuote: async (symbolKey: string) => {
    // set({ isLoading: true }); // Optional: don't block UI for quote
    try {
      const res = await stocksApi.getQuote(symbolKey);
      const quote = res;
      set((state) => ({
        quotes: {
          ...state.quotes,
          [symbolKey]: quote,
        },
      }));
    } catch (e) {
      console.error(e);
    } finally {
      // set({ isLoading: false });
    }
  },

  setLoading: (isLoading) =>
    set({
      isLoading,
    }),
}));
