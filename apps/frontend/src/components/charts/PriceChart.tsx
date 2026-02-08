import { useEffect, useRef, useState } from 'react';
import { createChart, CandlestickSeries } from 'lightweight-charts';
import { Radio, Spin } from 'antd';
import { stocksApi } from '../../api/stocks.api';

interface PriceChartProps {
  symbolKey: string;
  assetType?: string;
  height?: number | string;
}

/**
 * 股價圖表 (TradingView Lightweight Charts v5.x)
 */
export function PriceChart({
  symbolKey,
  assetType = 'STOCK',
  height = 400,
}: PriceChartProps) {
  const containerRef = useRef<HTMLDivElement>(null);
  const chartRef = useRef<ReturnType<typeof createChart> | null>(null);
  const [interval, setInterval] = useState<'1d' | '1w' | '1mo'>('1d');
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (!containerRef.current) return;

    // 1. 初始化圖表
    const chart = createChart(containerRef.current, {
      width: containerRef.current.clientWidth || 600,
      height: Number(height) || 400,
      layout: {
        background: { color: '#ffffff' },
        textColor: '#333333',
      },
      grid: {
        vertLines: { color: '#f0f0f0' },
        horzLines: { color: '#f0f0f0' },
      },
    });

    chartRef.current = chart;

    // 2. 建立系列 (v5.x API: addSeries + CandlestickSeries)
    const candlestickSeries = chart.addSeries(CandlestickSeries, {
      upColor: '#26a69a',
      downColor: '#ef5350',
      borderVisible: false,
      wickUpColor: '#26a69a',
      wickDownColor: '#ef5350',
    });

    // 3. 抓取資料
    const fetchData = async () => {
      setLoading(true);
      try {
        const res = await stocksApi.getCandles(symbolKey, interval);
        const candles = res;

        if (candles && Array.isArray(candles)) {
          const chartData = candles.map((c) => ({
            time: c.timestamp.split('T')[0],
            open: Number(c.open),
            high: Number(c.high),
            low: Number(c.low),
            close: Number(c.close),
          }));

          chartData.sort((a, b) => (a.time > b.time ? 1 : -1));
          candlestickSeries.setData(chartData);
          chart.timeScale().fitContent();
        }
      } catch (e) {
        console.error('Failed to fetch candles', e);
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
    };
  }, [symbolKey, interval, height]);

  // 根據資產類型決定可選的週期
  const isWarrant = assetType === 'WARRANT';

  return (
    <div style={{ position: 'relative' }}>
      <div style={{ marginBottom: 12, display: 'flex', justifyContent: 'flex-end' }}>
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
