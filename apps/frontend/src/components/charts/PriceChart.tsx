import React, { useEffect, useRef } from 'react';
import { createChart, CandlestickSeries } from 'lightweight-charts';
import { stocksApi } from '../../api/stocks.api';
import type { Candle } from '../../api/stocks.api';
import type { ApiResponse } from '../../types/api';

interface PriceChartProps {
  symbolKey: string;
  interval?: '1d' | '1w' | '1m' | '1y';
  height?: number | string;
}

/**
 * 股價圖表 (TradingView Lightweight Charts v5.x)
 */
export function PriceChart({
  symbolKey,
  interval = '1d',
  height = 400,
}: PriceChartProps) {
  const containerRef = useRef<HTMLDivElement>(null);
  const chartRef = useRef<any>(null);

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
      try {
        const res = await stocksApi.getCandles(symbolKey, interval);
        const candles = (res as unknown as ApiResponse<Candle[]>).data;

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

  return (
    <div
      ref={containerRef}
      style={{
        height,
        border: '1px solid #f0f0f0',
        borderRadius: 4,
        overflow: 'hidden',
      }}
    />
  );
}
