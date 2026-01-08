/**
 * 數字與金額格式化工具
 */

/**
 * 格式化數字（千位分隔符）
 */
export const formatNumber = (
  value: number | string,
  decimals: number = 2
): string => {
  const num = typeof value === "string" ? parseFloat(value) : value;
  if (isNaN(num)) return "0";
  return num.toLocaleString("zh-TW", {
    minimumFractionDigits: decimals,
    maximumFractionDigits: decimals,
  });
};

/**
 * 格式化金額（含幣別符號）
 */
export const formatCurrency = (
  value: number | string,
  currency: string = "TWD",
  decimals: number = 2
): string => {
  const symbols: Record<string, string> = {
    TWD: "NT$",
    USD: "$",
    CNY: "¥",
  };
  const symbol = symbols[currency] || currency;
  const formatted = formatNumber(value, decimals);
  return `${symbol} ${formatted}`;
};

/**
 * 格式化百分比
 */
export const formatPercent = (
  value: number | string,
  decimals: number = 2
): string => {
  const num = typeof value === "string" ? parseFloat(value) : value;
  if (isNaN(num)) return "0%";
  return `${num.toFixed(decimals)}%`;
};

/**
 * 格式化股價（通常 2-4 位小數）
 */
export const formatPrice = (value: number | string): string => {
  return formatNumber(value, 2);
};

/**
 * 格式化股數（可能是小數）
 */
export const formatQuantity = (value: number | string): string => {
  return formatNumber(value, 2);
};

/**
 * 損益顏色判斷（用於 UI）
 */
export const getProfitLossColor = (value: number | string): string => {
  const num = typeof value === "string" ? parseFloat(value) : value;
  if (num > 0) return "#52c41a"; // 綠色（盈利）
  if (num < 0) return "#f5222d"; // 紅色（虧損）
  return "#000000"; // 黑色（持平）
};
