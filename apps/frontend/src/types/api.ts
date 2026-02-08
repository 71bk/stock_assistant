/**
 * 共用 API 型別
 */

export type ApiResponse<T> = {
  success: true;
  data: T;
  error: null;
  traceId: string;
} | {
  success: false;
  data: T | null;
  error: {
    code: string;
    message: string;
    details?: Record<string, unknown>;
  };
  traceId: string;
};

export interface PageData<T> {
  items: T[];
  page: number;
  size: number;
  total: number;
}

export interface PageResponse<T> {
  success: boolean;
  data: PageData<T>;
  error: null;
  traceId: string;
}

// 錯誤碼列舉
export const ErrorCode = {
  VALIDATION_ERROR: "VALIDATION_ERROR",
  AUTH_UNAUTHORIZED: "AUTH_UNAUTHORIZED",
  AUTH_FORBIDDEN: "AUTH_FORBIDDEN",
  NOT_FOUND: "NOT_FOUND",
  CONFLICT: "CONFLICT",
  OCR_PARSE_FAILED: "OCR_PARSE_FAILED",
  RATE_LIMITED: "RATE_LIMITED",
  INTERNAL_ERROR: "INTERNAL_ERROR",
} as const;

