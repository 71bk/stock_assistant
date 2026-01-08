/**
 * 圖表指標工具函數
 * - MA（移動平均線）
 * - RSI（相對強弱指數）
 * - MACD（指數平滑異動平均線）
 * 等等
 */

/**
 * 計算簡單移動平均線（SMA）
 */
export const calculateSMA = (
  prices: number[],
  period: number
): number[] => {
  const sma: number[] = [];
  for (let i = period - 1; i < prices.length; i++) {
    const avg =
      prices.slice(i - period + 1, i + 1).reduce((a, b) => a + b) / period;
    sma.push(avg);
  }
  return sma;
};

/**
 * 計算相對強弱指數（RSI）
 */
export const calculateRSI = (
  prices: number[],
  period: number = 14
): number[] => {
  const rsi: number[] = [];
  const diffs = prices.slice(1).map((price, i) => price - prices[i]);

  let avgGain = 0;
  let avgLoss = 0;

  for (let i = 0; i < period; i++) {
    if (diffs[i] > 0) {
      avgGain += diffs[i];
    } else {
      avgLoss -= diffs[i];
    }
  }

  avgGain /= period;
  avgLoss /= period;

  for (let i = period; i < diffs.length; i++) {
    avgGain =
      (avgGain * (period - 1) + (diffs[i] > 0 ? diffs[i] : 0)) / period;
    avgLoss =
      (avgLoss * (period - 1) + (diffs[i] < 0 ? -diffs[i] : 0)) / period;

    const rs = avgGain / avgLoss;
    rsi.push(100 - 100 / (1 + rs));
  }

  return rsi;
};
