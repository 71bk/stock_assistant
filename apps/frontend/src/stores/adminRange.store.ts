import { create } from 'zustand';
import dayjs, { type Dayjs } from 'dayjs';

export type AdminDateRange = [Dayjs, Dayjs];

const defaultRange = (): AdminDateRange => [dayjs().subtract(6, 'day'), dayjs()];

interface AdminRangeState {
  range: AdminDateRange;
  setRange: (range: AdminDateRange) => void;
}

/**
 * Shared date range for all admin analytics pages.
 * Keeping it in a store means changing the range on one page is remembered
 * when navigating to another analytics page.
 */
export const useAdminRangeStore = create<AdminRangeState>((set) => ({
  range: defaultRange(),
  setRange: (range) => set({ range }),
}));
