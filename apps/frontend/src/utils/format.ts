import dayjs from 'dayjs';
import utc from 'dayjs/plugin/utc';
import timezone from 'dayjs/plugin/timezone';

dayjs.extend(utc);
dayjs.extend(timezone);

export const DATE_FORMAT = 'YYYY-MM-DD';
export const TIME_FORMAT = 'HH:mm:ss';
export const DATETIME_FORMAT = 'YYYY-MM-DD HH:mm:ss';

export const DATETIME_LONG_FORMAT = 'YYYY-MM-DD HH:mm:ss.SSS';

/**
 * Parses any date input to a UTC Dayjs object.
 * Backend always returns UTC ISO strings.
 */
export const toUtc = (date?: string | Date | dayjs.Dayjs) => {
  return dayjs(date).utc();
};

/**
 * Checks if a date is today in the given timezone.
 */
export const isToday = (dateStr: string, tz: string = dayjs.tz.guess()) => {
  if (!dateStr) return false;
  return dayjs.utc(dateStr).tz(tz).isSame(dayjs().tz(tz), 'day');
};

/**
 * Gets the current time in UTC string.
 */
export const getNowUtc = () => {
  return dayjs.utc().toISOString();
};

/**
 * Formats a UTC date string to the user's local timezone (or specified timezone).
 */
export const formatDateTime = (
  utcDateStr: string,
  tz: string = dayjs.tz.guess(),
  format: string = DATETIME_FORMAT
) => {
  if (!utcDateStr) return '-';
  return dayjs.utc(utcDateStr).tz(tz).format(format);
};

/**
 * Formats a number as currency.
 */
export const formatCurrency = (amount: number, currency: string = 'TWD') => {
  return new Intl.NumberFormat('zh-TW', {
    style: 'currency',
    currency,
    minimumFractionDigits: 0,
    maximumFractionDigits: 2,
  }).format(amount);
};

/**
 * 自動校正 Markdown 格式，修復 LLM 可能產生的排版問題
 * 1. 確保表格 (|) 前有空行
 * 2. 確保清單 (-) 前有換行
 */
export const preprocessMarkdown = (content: string) => {
  if (!content) return '';
  
  return content
    // 1. 修正表格格式：在表格開始符號 (|) 前如果緊接文字且沒有空行，則補上空行
    // 只針對行首的 | (或文字後的第一個 |) 做處理，避免破壞表格內部的 |
    .replace(/([^ \n])(\|(?=[^|\n]+\|))/g, '$1\n\n$2')
    
    // 2. 修正清單格式：在清單符號 (- 或 *) 前如果緊接文字，則補上換行
    // 必須確保後面跟著空格，才是清單特徵，避免破壞日期 (2024-01-01)
    .replace(/([^ \n])\s?([-*]\s)/g, '$1\n$2')
    
    // 3. 清理過多的連續空行
    .replace(/\n{3,}/g, '\n\n');
};