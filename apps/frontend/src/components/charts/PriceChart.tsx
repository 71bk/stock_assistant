import { useEffect, useRef } from "react";
import { createChart, ColorType } from "lightweight-charts";
import { stocksApi } from "../../api/stocks.api";
import type { Candle } from "../../api/stocks.api";
import type { ApiResponse } from "../../types/api";

interface PriceChartProps {
  symbolKey: string;
  interval?: "1d" | "1w" | "1m" | "1y";
  height?: number | string;
}

/**
 * 股價圖表 (TradingView Lightweight Charts)
 */
export function PriceChart({
  symbolKey,
  interval = "1d",
  height = 400,
}: PriceChartProps) {
  const containerRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!containerRef.current) return;

    const chart = createChart(containerRef.current, {
      layout: {
        background: { type: ColorType.Solid, color: "white" },
        textColor: "black",
      },
      width: containerRef.current.clientWidth,
      height: typeof height === "number" ? height : 400,
      grid: {
        vertLines: { color: "#f0f0f0" },
        horzLines: { color: "#f0f0f0" },
      },
    });

    const candlestickSeries = chart.addCandlestickSeries({
      upColor: "#26a69a",
      downColor: "#ef5350",
      borderVisible: false,
      wickUpColor: "#26a69a",
      wickDownColor: "#ef5350",
    });

    // Fetch data
    const fetchData = async () => {
      try {
        const res = await stocksApi.getCandles(symbolKey, interval);
        const candles = (res as unknown as ApiResponse<Candle[]>).data;
        
        // Convert to Lightweight Charts format
        const chartData = candles.map((c) => ({
          time: c.ts.split("T")[0], // YYYY-MM-DD
          open: c.o,
          high: c.h,
          low: c.l,
          close: c.c,
        }));

        // Sort by time just in case
        chartData.sort((a, b) => (a.time > b.time ? 1 : -1));

        candlestickSeries.setData(chartData);
        chart.timeScale().fitContent();
      } catch (e) {
        console.error("Failed to fetch candles", e);
      }
    };

    fetchData();

    // Resize handler
    const handleResize = () => {
      if (containerRef.current) {
        chart.applyOptions({ width: containerRef.current.clientWidth });
      }
    };

    window.addEventListener("resize", handleResize);

    return () => {
      window.removeEventListener("resize", handleResize);
      chart.remove();
    };
  }, [symbolKey, interval, height]);

  return (
    <div
      ref={containerRef}
      style={{
        height,
        border: "1px solid #f0f0f0",
        borderRadius: 4,
        overflow: "hidden",
      }}
    />
  );
}
