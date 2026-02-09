import * as Sentry from "@sentry/react";

/**
 * 統一的日誌記錄器
 * 會將錯誤上報至 Sentry
 */
export const logger = {
  /**
   * 記錄錯誤日誌
   * @param msg 錯誤訊息
   * @param err 錯誤物件
   * @param ctx 額外上下文
   */
  error: (msg: string, err?: unknown, ctx?: Record<string, unknown>) => {
    // 開發環境或明確需要時輸出到控制台
    console.error(msg, err);

    // 上報至 Sentry
    Sentry.captureException(err || new Error(msg), {
      tags: { category: "app_error" },
      contexts: { custom: ctx as Record<string, unknown> },
    });
  },

  /**
   * 記錄警告日誌
   */
  warn: (msg: string, ctx?: Record<string, unknown>) => {
    console.warn(msg, ctx);
    Sentry.captureMessage(msg, {
      level: "warning",
      contexts: { custom: ctx as Record<string, unknown> },
    });
  },

  /**
   * 記錄一般資訊
   */
  info: (msg: string, ctx?: Record<string, unknown>) => {
    console.info(msg, ctx);
  },
};
