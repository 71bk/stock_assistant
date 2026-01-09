import dayjs from 'dayjs';
import utc from 'dayjs/plugin/utc';
import timezone from 'dayjs/plugin/timezone';

dayjs.extend(utc);
dayjs.extend(timezone);

export const DATE_FORMAT = 'YYYY-MM-DD';
export const TIME_FORMAT = 'HH:mm:ss';
export const DATETIME_FORMAT = 'YYYY-MM-DD HH:mm:ss';

/**
 * Parses any date input to a UTC Dayjs object.
 * Backend always returns UTC ISO strings.
 */
export const toUtc = (date?: string | Date | dayjs.Dayjs) => {
  return dayjs(date).utc();
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