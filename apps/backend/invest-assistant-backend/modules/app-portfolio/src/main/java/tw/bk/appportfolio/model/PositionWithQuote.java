package tw.bk.appportfolio.model;

import java.math.BigDecimal;

/**
 * 持倉資料（含報價與損益計算結果）
 */
public record PositionWithQuote(
        Long portfolioId,
        Long instrumentId,
        String ticker,
        String name,
        BigDecimal totalQuantity,
        BigDecimal avgCostNative,
        String currency,
        // 報價與損益
        BigDecimal currentPrice,
        BigDecimal marketValue,
        BigDecimal unrealizedPnl,
        BigDecimal unrealizedPnlPercent) {
    /**
     * 建立不含報價資料的持倉（損益欄位為 null）
     */
    public static PositionWithQuote withoutQuote(
            Long portfolioId,
            Long instrumentId,
            String ticker,
            String name,
            BigDecimal totalQuantity,
            BigDecimal avgCostNative,
            String currency) {
        return new PositionWithQuote(
                portfolioId,
                instrumentId,
                ticker,
                name,
                totalQuantity,
                avgCostNative,
                currency,
                null, null, null, null);
    }
}
