/**
 * 共用 API 型別
 */

export interface ApiResponse<T> {
  success: boolean;
  data: T;
  error: null;
  traceId: string;
}

export interface ApiError {
  success: false;
  data: null;
  error: {
    code: string;
    message: string;
    details?: Record<string, any>;
  };
  traceId: string;
}

export interface PageResponse<T> {
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

// 錯誤碼列舉
export enum ErrorCode {
  VALIDATION_ERROR = "VALIDATION_ERROR",
  AUTH_UNAUTHORIZED = "AUTH_UNAUTHORIZED",
  AUTH_FORBIDDEN = "AUTH_FORBIDDEN",
  NOT_FOUND = "NOT_FOUND",
  CONFLICT = "CONFLICT",
  OCR_PARSE_FAILED = "OCR_PARSE_FAILED",
  RATE_LIMITED = "RATE_LIMITED",
  INTERNAL_ERROR = "INTERNAL_ERROR",
}
