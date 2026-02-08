import { useCallback } from 'react';
import { useImportStore } from '../stores/import.store';
import { usePortfolioStore } from '../stores/portfolio.store';
import { useApiError } from './useApiError';

/**
 * Custom Hook to coordinate Import Store and Portfolio Store
 */
export function useImportFlow() {
  const importStore = useImportStore();
  const portfolioStore = usePortfolioStore();
  const { handleError } = useApiError();

  const uploadFile = useCallback(async (file: File) => {
    try {
      const portfolioId = await portfolioStore.initPortfolioId();
      if (!portfolioId) {
        // Notification is already shown in initPortfolioId
        return;
      }
      await importStore.uploadFile(file, portfolioId);
    } catch (error) {
      handleError(error, '檔案上傳失敗');
    }
  }, [importStore, portfolioStore, handleError]);

  const reprocessJob = useCallback(async () => {
    try {
      const portfolioId = await portfolioStore.initPortfolioId();
      if (!portfolioId) return;
      await importStore.reprocessJob(portfolioId);
    } catch (error) {
      handleError(error, '重新解析失敗');
    }
  }, [importStore, portfolioStore, handleError]);

  const confirmTrades = useCallback(async (selectedIds: string[]) => {
    try {
      await importStore.confirmTrades(selectedIds);
    } catch (error) {
      handleError(error, '匯入確認失敗');
    }
  }, [importStore, handleError]);

  return {
    ...importStore,
    uploadFile,
    reprocessJob,
    confirmTrades,
  };
}
