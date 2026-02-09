/**
 * 圖表指標工具函數
 * - SMA (Simple Moving Average)
 * - EMA (Exponential Moving Average)
 * - RSI (Relative Strength Index)
 * - MACD (Moving Average Convergence Divergence)
 */

export interface ChartDataPoint {
  time: string;
  value: number;
}

export interface OHLCData {
  time: string;
  open: number;
  high: number;
  low: number;
  close: number;
}

/**
 * 計算簡單移動平均線 (SMA)
 */
export const calculateSMA = (
  data: OHLCData[],
  period: number
): ChartDataPoint[] => {
  const sma: ChartDataPoint[] = [];
  if (data.length < period) return sma;

  for (let i = period - 1; i < data.length; i++) {
    const sum = data.slice(i - period + 1, i + 1).reduce((acc, curr) => acc + curr.close, 0);
    sma.push({
      time: data[i].time,
      value: sum / period,
    });
  }
  return sma;
};

/**
 * 計算指數移動平均線 (EMA)
 */
export const calculateEMA = (
  data: OHLCData[],
  period: number
): ChartDataPoint[] => {
  const ema: ChartDataPoint[] = [];
  if (data.length < period) return ema;

  const k = 2 / (period + 1);
  
  // 第一個 EMA 使用 SMA 作為起始值
  let initialSum = 0;
  for (let i = 0; i < period; i++) {
    initialSum += data[i].close;
  }
  let prevEma = initialSum / period;
  
  // 記錄第一個 EMA 點
  ema.push({
    time: data[period - 1].time,
    value: prevEma
  });

  // 計算後續的 EMA
  for (let i = period; i < data.length; i++) {
    const currentPrice = data[i].close;
    const currentEma = (currentPrice - prevEma) * k + prevEma;
    ema.push({
      time: data[i].time,
      value: currentEma
    });
    prevEma = currentEma;
  }

  return ema;
};

/**
 * 計算相對強弱指數 (RSI)
 */
export const calculateRSI = (
  data: OHLCData[],
  period: number = 14
): ChartDataPoint[] => {
  const rsi: ChartDataPoint[] = [];
  if (data.length <= period) return rsi;

  let gains = 0;
  let losses = 0;

  // 計算初始平均漲跌幅
  for (let i = 1; i <= period; i++) {
    const change = data[i].close - data[i - 1].close;
    if (change > 0) {
      gains += change;
    } else {
      losses -= change;
    }
  }

  let avgGain = gains / period;
  let avgLoss = losses / period;

  // 計算第一個 RSI
  let rs = avgLoss === 0 ? 100 : avgGain / avgLoss;
  const firstRsi = 100 - (100 / (1 + rs));
  
  rsi.push({
    time: data[period].time,
    value: firstRsi
  });

  // 計算後續 RSI
  for (let i = period + 1; i < data.length; i++) {
    const change = data[i].close - data[i - 1].close;
    const currentGain = change > 0 ? change : 0;
    const currentLoss = change < 0 ? -change : 0;

    avgGain = (avgGain * (period - 1) + currentGain) / period;
    avgLoss = (avgLoss * (period - 1) + currentLoss) / period;

    rs = avgLoss === 0 ? 100 : avgGain / avgLoss;
    const currentRsi = 100 - (100 / (1 + rs));

    rsi.push({
      time: data[i].time,
      value: currentRsi
    });
  }

  return rsi;
};

export interface MACDResult {
  macd: ChartDataPoint[];
  signal: ChartDataPoint[];
  histogram: ChartDataPoint[];
}

/**
 * 計算 MACD
 * @param fastPeriod 快線週期 (預設 12)
 * @param slowPeriod 慢線週期 (預設 26)
 * @param signalPeriod 訊號線週期 (預設 9)
 */
export const calculateMACD = (
  data: OHLCData[],
  fastPeriod: number = 12,
  slowPeriod: number = 26,
  signalPeriod: number = 9
): MACDResult => {
  const result: MACDResult = {
    macd: [],
    signal: [],
    histogram: []
  };

  if (data.length < slowPeriod + signalPeriod) return result;

  // 1. 計算 Fast EMA 和 Slow EMA
  const fastEMA = calculateEMA(data, fastPeriod);
  const slowEMA = calculateEMA(data, slowPeriod);

  // 2. 計算 MACD Line (Fast EMA - Slow EMA)
  // 注意：EMA 陣列長度不同，需要對齊時間
  // Slow EMA 開始的時間點較晚，以此為基準
  const macdLine: ChartDataPoint[] = [];
  
  // 建立 Fast EMA 的 Map 以便快速查找
  const fastEMAMap = new Map(fastEMA.map(item => [item.time, item.value]));

  for (const sEma of slowEMA) {
    const fEmaValue = fastEMAMap.get(sEma.time);
    if (fEmaValue !== undefined) {
      macdLine.push({
        time: sEma.time,
        value: fEmaValue - sEma.value
      });
    }
  }

  // 3. 計算 Signal Line (MACD Line 的 EMA)
  // 這裡需要把 macdLine 轉換格式傳入 calculateEMA 邏輯
  // 但 calculateEMA 接受 OHLCData[]，我們需要重用核心 EMA 邏輯或適配一下
  // 為了簡單，我們這裡手動計算 Signal Line (EMA of MACD Line)
  
  const signalLine: ChartDataPoint[] = [];
  if (macdLine.length >= signalPeriod) {
    const k = 2 / (signalPeriod + 1);
    
    // Signal Line 初始值 (SMA of MACD Line)
    let sum = 0;
    for (let i = 0; i < signalPeriod; i++) {
      sum += macdLine[i].value;
    }
    let prevSignal = sum / signalPeriod;

    signalLine.push({
      time: macdLine[signalPeriod - 1].time,
      value: prevSignal
    });

    for (let i = signalPeriod; i < macdLine.length; i++) {
      const currentMacd = macdLine[i].value;
      const currentSignal = (currentMacd - prevSignal) * k + prevSignal;
      signalLine.push({
        time: macdLine[i].time,
        value: currentSignal
      });
      prevSignal = currentSignal;
    }
  }

  // 4. 計算 Histogram (MACD Line - Signal Line)
  const signalLineMap = new Map(signalLine.map(item => [item.time, item.value]));
  const histogram: ChartDataPoint[] = [];

  for (const macd of macdLine) {
    const signalValue = signalLineMap.get(macd.time);
    if (signalValue !== undefined) {
      histogram.push({
        time: macd.time,
        value: macd.value - signalValue
      });
    }
  }
  
  // 重新整理回傳結果，確保只回傳重疊的部分 (通常以 Signal Line 的時間為主)
  // 但為了讓圖表盡可能多顯示數據，我們可以保留 macdLine 即使沒有 signalLine
  // 不過為了對齊，通常還是以最短的 signalLine 為主比較好看，或者都保留
  // 這裡選擇都保留，前端繪圖時會根據時間軸自動對齊
  result.macd = macdLine;
  result.signal = signalLine;
  result.histogram = histogram;

  return result;
};
