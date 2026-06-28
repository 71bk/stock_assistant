import { useCallback, useEffect, useMemo, useState } from 'react';
import { useAdminRangeStore } from '../../../stores/adminRange.store';

interface AnalyticsQuery {
  from: string;
  to: string;
  timezone: string;
}

/** Build the API query object from the shared admin date range. */
export function useAnalyticsQuery(): AnalyticsQuery {
  const range = useAdminRangeStore((s) => s.range);
  return useMemo(
    () => ({
      from: range[0].format('YYYY-MM-DD'),
      to: range[1].format('YYYY-MM-DD'),
      timezone: 'Asia/Taipei',
    }),
    [range],
  );
}

interface AnalyticsLoad<T> {
  data: T | null;
  loading: boolean;
  error: string | null;
  reload: () => void;
}

/**
 * Fetch a single analytics resource, re-running whenever the shared date range
 * changes. Each section owns its own fetch so pages stay thin and independent.
 */
export function useAnalyticsResource<T>(
  fetcher: (query: AnalyticsQuery) => Promise<T>,
): AnalyticsLoad<T> {
  const query = useAnalyticsQuery();
  const [data, setData] = useState<T | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setData(await fetcher(query));
    } catch (err) {
      setError(err instanceof Error ? err.message : '分析資料載入失敗');
    } finally {
      setLoading(false);
    }
    // fetcher is expected to be stable (module-level api function)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [query]);

  useEffect(() => {
    load();
  }, [load]);

  return { data, loading, error, reload: load };
}
