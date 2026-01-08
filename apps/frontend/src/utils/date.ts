import dayjs from "dayjs";
import utc from "dayjs/plugin/utc";
import timezone from "dayjs/plugin/timezone";

dayjs.extend(utc);
dayjs.extend(timezone);

/**
 * 日期/時間工具函數
 * - 所有時間戳在後端以 UTC 存儲
 * - 前端根據使用者時區顯示
 */

/**
 * 解析 UTC 時間戳，轉換到指定時區
 */
export const parseUTC = (
  timestamp: string,
  timezone: string = "Asia/Taipei"
): dayjs.Dayjs => {
  return dayjs(timestamp).tz(timezone);
};

/**
 * 格式化時間戳（UTC → 指定時區）
 */
export const formatDateTime = (
  timestamp: string,
  format: string = "YYYY-MM-DD HH:mm:ss",
  timezone: string = "Asia/Taipei"
): string => {
  return parseUTC(timestamp, timezone).format(format);
};

/**
 * 格式化日期
 */
export const formatDate = (
  dateStr: string,
  format: string = "YYYY-MM-DD"
): string => {
  return dayjs(dateStr).format(format);
};

/**
 * 取得目前時間（UTC）
 */
export const getNowUTC = (): string => {
  return dayjs.utc().toISOString();
};

/**
 * 是否是今天（相對於指定時區）
 */
export const isToday = (
  dateStr: string,
  timezone: string = "Asia/Taipei"
): boolean => {
  const targetDate = parseUTC(dateStr, timezone);
  const today = dayjs.tz(timezone);
  return targetDate.format("YYYY-MM-DD") === today.format("YYYY-MM-DD");
};
