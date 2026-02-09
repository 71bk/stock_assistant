import { useEffect, useRef, useState } from 'react';
import { createChart, CandlestickSeries, LineSeries, HistogramSeries, type IChartApi, type ISeriesApi } from 'lightweight-charts';
import { Radio, Spin, Checkbox, Popover, Button, InputNumber } from 'antd';
import { SettingOutlined } from '@ant-design/icons';
import { logger } from '../../utils/logger';
import { stocksApi } from '../../api/stocks.api';
import { calculateSMA, calculateEMA, calculateRSI, calculateMACD } from './indicators';
import type { OHLCData } from './indicators';

interface PriceChartProps {
  symbolKey: string;
  assetType?: string;
  height?: number | string;
}

interface ChartState {
  showSMA: boolean;
  smaPeriod: number;
  showEMA: boolean;
  emaPeriod: number;
  showRSI: boolean;
  rsiPeriod: number;
  showMACD: boolean;
}

/**
 * 股價圖表 (TradingView Lightweight Charts v5.x)
 */
export function PriceChart({
  symbolKey,
  assetType = 'STOCK',
  height = 500, // 增加預設高度以容納指標
}: PriceChartProps) {
  const containerRef = useRef<HTMLDivElement>(null);
  const chartRef = useRef<IChartApi | null>(null);
  const [interval, setInterval] = useState<'1d' | '1w' | '1mo'>('1d');
  const [loading, setLoading] = useState(false);

  // 指標狀態
  const [indicators, setIndicators] = useState<ChartState>({
    showSMA: false,
    smaPeriod: 20,
    showEMA: false,
    emaPeriod: 20,
    showRSI: false,
    rsiPeriod: 14,
    showMACD: false,
  });

  // 保存原始數據以供重新計算指標
  const [ohlcData, setOhlcData] = useState<OHLCData[]>([]);

  // 引用各個 Series 以便更新
  const seriesRef = useRef<{
    candlestick?: ISeriesApi<"Candlestick">;
    sma?: ISeriesApi<"Line">;
    ema?: ISeriesApi<"Line">;
    rsi?: ISeriesApi<"Line">;
    macdLine?: ISeriesApi<"Line">;
    macdSignal?: ISeriesApi<"Line">;
    macdHist?: ISeriesApi<"Histogram">;
  }>({});

  useEffect(() => {
    if (!containerRef.current) return;

    // 1. 初始化圖表
    // 為了支援指標，我們可能需要調整佈局配置
    const chart = createChart(containerRef.current, {
      width: containerRef.current.clientWidth || 600,
      height: Number(height) || 500,
      layout: {
        background: { color: '#ffffff' },
        textColor: '#333333',
      },
      grid: {
        vertLines: { color: '#f0f0f0' },
        horzLines: { color: '#f0f0f0' },
      },
      rightPriceScale: {
        visible: true,
        borderColor: '#f0f0f0',
      },
      timeScale: {
        borderColor: '#f0f0f0',
        timeVisible: true,
      },
    });

    chartRef.current = chart;

    // 2. 建立主圖表 Series
    const candlestickSeries = chart.addSeries(CandlestickSeries, {
      upColor: '#26a69a',
      downColor: '#ef5350',
      borderVisible: false,
      wickUpColor: '#26a69a',
      wickDownColor: '#ef5350',
    });
    seriesRef.current.candlestick = candlestickSeries;

    // 3. 抓取資料
    const fetchData = async () => {
      setLoading(true);
      try {
        const res = await stocksApi.getCandles(symbolKey, interval);
        const candles = res;

        if (candles && Array.isArray(candles)) {
          const rawData: OHLCData[] = candles.map((c) => ({
            time: c.timestamp.split('T')[0],
            open: Number(c.open),
            high: Number(c.high),
            low: Number(c.low),
            close: Number(c.close),
          }));

          rawData.sort((a, b) => (a.time > b.time ? 1 : -1));
          setOhlcData(rawData); // 保存原始數據
          
          candlestickSeries.setData(rawData);
          chart.timeScale().fitContent();
        }
      } catch (e) {
        logger.error('Failed to fetch candles', e);
      } finally {
        setLoading(false);
      }
    };

    fetchData();

    const handleResize = () => {
      if (containerRef.current && chartRef.current) {
        chartRef.current.applyOptions({ width: containerRef.current.clientWidth });
      }
    };

    window.addEventListener('resize', handleResize);

    return () => {
      window.removeEventListener('resize', handleResize);
      chart.remove();
      chartRef.current = null;
      seriesRef.current = {};
    };
  }, [symbolKey, interval, height]); // 當這些改變時重建整個圖表

  // 4. 響應指標狀態變化，增刪 Series 或更新數據
  useEffect(() => {
    if (!chartRef.current || ohlcData.length === 0) return;
    const chart = chartRef.current;
    
    // 清除舊指標 Series (如果存在)
    // 這裡為了簡單，我們採用 "移除再重建" 的策略，或者 "檢查是否存在並更新"
    // 由於 lightweight-charts 移除 series 需要 reference，我們用 seriesRef 保存
    
    // --- SMA ---
    if (seriesRef.current.sma) {
      chart.removeSeries(seriesRef.current.sma);
      delete seriesRef.current.sma;
    }
    if (indicators.showSMA) {
      const smaData = calculateSMA(ohlcData, indicators.smaPeriod);
      const smaSeries = chart.addSeries(LineSeries, {
        color: '#2962FF',
        lineWidth: 2,
        title: `SMA ${indicators.smaPeriod}`,
        priceScaleId: 'right', // 與 K 線同軸
      });
      smaSeries.setData(smaData);
      seriesRef.current.sma = smaSeries;
    }

    // --- EMA ---
    if (seriesRef.current.ema) {
      chart.removeSeries(seriesRef.current.ema);
      delete seriesRef.current.ema;
    }
    if (indicators.showEMA) {
      const emaData = calculateEMA(ohlcData, indicators.emaPeriod);
      const emaSeries = chart.addSeries(LineSeries, {
        color: '#FF6D00',
        lineWidth: 2,
        title: `EMA ${indicators.emaPeriod}`,
        priceScaleId: 'right',
      });
      emaSeries.setData(emaData);
      seriesRef.current.ema = emaSeries;
    }

    // --- RSI (獨立窗格) ---
    // Lightweight Charts 不直接支援 "Pane"，但可以透過 priceScaleId 和 scaleMargins 來模擬
    // 或是將主圖縮小，留出空間給副指標
    
    // 我們這裡採用 scaleMargins 策略：
    // 主圖 (Price + MA) 佔據上面 70%
    // RSI/MACD 佔據下面 30% (如果開啟)
    
    // 計算需要幾個副圖層
    let bottomPanes = 0;
    if (indicators.showRSI) bottomPanes++;
    if (indicators.showMACD) bottomPanes++;
    
    const mainPaneHeight = bottomPanes === 0 ? 1 : (bottomPanes === 1 ? 0.7 : 0.6);
    
    // 設定主圖區域
    seriesRef.current.candlestick?.applyOptions({
      priceScaleId: 'right',
    });
    chart.priceScale('right').applyOptions({
      scaleMargins: {
        top: 0.1,
        bottom: 1 - mainPaneHeight + 0.05, // 留一點緩衝
      },
    });

    // --- RSI ---
    if (seriesRef.current.rsi) {
      chart.removeSeries(seriesRef.current.rsi);
      delete seriesRef.current.rsi;
    }
    if (indicators.showRSI) {
      const rsiData = calculateRSI(ohlcData, indicators.rsiPeriod);
      const rsiSeries = chart.addSeries(LineSeries, {
        color: '#9C27B0',
        lineWidth: 2,
        title: `RSI ${indicators.rsiPeriod}`,
        priceScaleId: 'rsi',
      });
      rsiSeries.setData(rsiData);
      seriesRef.current.rsi = rsiSeries;

      // 設定 RSI 的 Scale
      // 如果只有 RSI，佔據下面 (1 - mainPaneHeight)
      // 如果有 RSI 和 MACD，RSI 佔據中間部分
      
      const rsiTop = mainPaneHeight;
      const rsiBottom = indicators.showMACD ? rsiTop + ((1 - mainPaneHeight) / 2) : 1;
      
      chart.priceScale('rsi').applyOptions({
        scaleMargins: {
          top: rsiTop, // 緊接在主圖下面
          bottom: 1 - rsiBottom,
        },
        visible: true,
      });
    }

    // --- MACD ---
    if (seriesRef.current.macdLine) {
      chart.removeSeries(seriesRef.current.macdLine);
      delete seriesRef.current.macdLine;
    }
    if (seriesRef.current.macdSignal) {
      chart.removeSeries(seriesRef.current.macdSignal);
      delete seriesRef.current.macdSignal;
    }
    if (seriesRef.current.macdHist) {
      chart.removeSeries(seriesRef.current.macdHist);
      delete seriesRef.current.macdHist;
    }

    if (indicators.showMACD) {
      const macdRes = calculateMACD(ohlcData);
      
      const macdHistSeries = chart.addSeries(HistogramSeries, {
        color: '#26a69a',
        priceScaleId: 'macd',
      });
      const macdLineSeries = chart.addSeries(LineSeries, {
        color: '#2962FF',
        lineWidth: 1,
        title: 'MACD',
        priceScaleId: 'macd',
      });
      const macdSignalSeries = chart.addSeries(LineSeries, {
        color: '#FF6D00',
        lineWidth: 1,
        title: 'Signal',
        priceScaleId: 'macd',
      });

      // 設定 Histogram 顏色邏輯 (需要逐點設定，這裡先簡單用統一色或基於值的顏色)
      // HistogramSeries setData 支援 { time, value, color }
      const coloredHist = macdRes.histogram.map(h => ({
        ...h,
        color: h.value >= 0 ? '#26a69a' : '#ef5350',
      }));

      macdHistSeries.setData(coloredHist);
      macdLineSeries.setData(macdRes.macd);
      macdSignalSeries.setData(macdRes.signal);

      seriesRef.current.macdHist = macdHistSeries;
      seriesRef.current.macdLine = macdLineSeries;
      seriesRef.current.macdSignal = macdSignalSeries;

      // 設定 MACD Scale
      const macdTop = indicators.showRSI ? (1 - (1 - mainPaneHeight) / 2) : mainPaneHeight;

      chart.priceScale('macd').applyOptions({
        scaleMargins: {
          top: macdTop,
          bottom: 0,
        },
        visible: true,
      });
    }

  }, [indicators, ohlcData]); // 當指標設定或數據改變時執行

  // 根據資產類型決定可選的週期
  const isWarrant = assetType === 'WARRANT';

  // 指標設定的 Popover 內容
  const indicatorContent = (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 12, width: 300 }}>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
        <Checkbox 
          checked={indicators.showSMA} 
          onChange={(e) => setIndicators(prev => ({ ...prev, showSMA: e.target.checked }))}
        >SMA</Checkbox>
        {indicators.showSMA && (
          <InputNumber 
            size="small" 
            min={1} 
            max={200} 
            value={indicators.smaPeriod}
            onChange={(val) => setIndicators(prev => ({ ...prev, smaPeriod: val || 20 }))}
          />
        )}
      </div>

      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
        <Checkbox 
          checked={indicators.showEMA} 
          onChange={(e) => setIndicators(prev => ({ ...prev, showEMA: e.target.checked }))}
        >EMA</Checkbox>
        {indicators.showEMA && (
          <InputNumber 
            size="small" 
            min={1} 
            max={200} 
            value={indicators.emaPeriod}
            onChange={(val) => setIndicators(prev => ({ ...prev, emaPeriod: val || 20 }))}
          />
        )}
      </div>

      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
        <Checkbox 
          checked={indicators.showRSI} 
          onChange={(e) => setIndicators(prev => ({ ...prev, showRSI: e.target.checked }))}
        >RSI</Checkbox>
        {indicators.showRSI && (
          <InputNumber 
            size="small" 
            min={1} 
            max={100} 
            value={indicators.rsiPeriod}
            onChange={(val) => setIndicators(prev => ({ ...prev, rsiPeriod: val || 14 }))}
          />
        )}
      </div>

      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
        <Checkbox 
          checked={indicators.showMACD} 
          onChange={(e) => setIndicators(prev => ({ ...prev, showMACD: e.target.checked }))}
        >MACD</Checkbox>
        {/* MACD 參數較複雜，這裡暫時使用預設值 12, 26, 9 */}
      </div>
    </div>
  );

  return (
    <div style={{ position: 'relative' }}>
      <div style={{ marginBottom: 12, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <div style={{ display: 'flex', alignItems: 'center' }}>
            <Popover content={indicatorContent} title="技術指標" trigger="click" placement="bottomLeft">
                <Button icon={<SettingOutlined />}>指標</Button>
            </Popover>
        </div>

        <Radio.Group 
          value={interval} 
          onChange={(e) => setInterval(e.target.value)}
          size="small"
          buttonStyle="solid"
        >
          <Radio.Button value="1d">日</Radio.Button>
          {!isWarrant && <Radio.Button value="1w">週</Radio.Button>}
          <Radio.Button value="1mo">月</Radio.Button>
        </Radio.Group>
      </div>

      {loading && (
        <div style={{ position: 'absolute', top: '50%', left: '50%', transform: 'translate(-50%, -50%)', zIndex: 10 }}>
          <Spin size="large" />
        </div>
      )}

      <div
        ref={containerRef}
        style={{
          height,
          border: '1px solid #f0f0f0',
          borderRadius: 4,
          overflow: 'hidden',
          backgroundColor: '#fff'
        }}
      />
    </div>
  );
}
