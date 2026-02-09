import { notification } from 'antd';
import { useCallback } from 'react';
import { logger } from '../utils/logger';

/**
 * Unified API Error Handling Hook
 */
export function useApiError() {
  const handleError = useCallback((error: unknown, context?: string) => {
    logger.error(`API Error [${context || 'Unknown'}]:`, error);

    let message = '發生未知錯誤，請稍後再試。';
    let description = '';

    if (error instanceof Error) {
      message = error.message;
      // Accessing the cause we attached in http.ts
      if (error.cause) {
        const cause = error.cause as Record<string, unknown>;
        const errObj = cause.error as Record<string, unknown> | undefined;
        if (errObj?.code) {
          description = `錯誤代碼: ${errObj.code}`;
        }
      }
    }

    notification.error({
      message: context || '操作失敗',
      description: description ? `${message} (${description})` : message,
      duration: 5,
    });
  }, []);

  return { handleError };
}
