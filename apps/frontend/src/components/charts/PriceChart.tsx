import { useEffect, useRef } from "react";

interface PriceChartProps {
  instrumentId: string;
  interval?: "1d" | "1w" | "1m" | "1y";
  height?: number | string;
}

/**
 * 股價圖表包裝器
 * 支援 ECharts 或 TradingView Lightweight Charts
 * 
 * TODO: 整合實際圖表庫（ECharts/Lightweight Charts）
 */
export function PriceChart({
  instrumentId,
  interval = "1d",
  height = 400,
}: PriceChartProps) {
  const containerRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    // 初始化圖表邏輯
    console.log(`Initializing chart for ${instrumentId}, interval: ${interval}`);
  }, [instrumentId, interval]);

  return (
    <div
      ref={containerRef}
      style={{
        height,
        border: "1px solid #f0f0f0",
        borderRadius: 4,
      }}
    >
      {/* 圖表會渲染在此 */}
      <div style={{ padding: "20px", textAlign: "center" }}>
        圖表即將實現...
      </div>
    </div>
  );
}
