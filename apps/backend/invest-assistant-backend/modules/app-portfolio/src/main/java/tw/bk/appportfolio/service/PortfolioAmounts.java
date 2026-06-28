package tw.bk.appportfolio.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import tw.bk.appcommon.enums.ErrorCode;
import tw.bk.appcommon.exception.BusinessException;

/**
 * Portfolio 金額/數量的共用刻度常數與正規化工具。
 *
 * <p>原本散落在 {@code PortfolioService} 的 scale 常數與 normalize 方法，
 * 拆分出的 {@code PositionService}/{@code PortfolioValuationService}/{@code TradeService}
 * 都會用到，集中於此避免各服務重複實作而漂移。
 */
final class PortfolioAmounts {
    static final int AMOUNT_SCALE = 6;
    static final int PRICE_SCALE = 8;
    static final int AVG_COST_SCALE = 8;
    static final String DEFAULT_BASE_CURRENCY = "TWD";

    private PortfolioAmounts() {
    }

    static BigDecimal zeroAmount() {
        return BigDecimal.ZERO.setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
    }

    static BigDecimal normalizeQuantity(BigDecimal quantity) {
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Quantity must be greater than 0");
        }
        return quantity.setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
    }

    static BigDecimal normalizePrice(BigDecimal price) {
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Price must be greater than 0");
        }
        return price.setScale(PRICE_SCALE, RoundingMode.HALF_UP);
    }

    static BigDecimal normalizeAmount(BigDecimal value) {
        if (value == null) {
            return zeroAmount();
        }
        if (value.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Amount cannot be negative");
        }
        return value.setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
    }

    static String normalizeBaseCurrency(String baseCurrency) {
        if (isBlank(baseCurrency)) {
            return DEFAULT_BASE_CURRENCY;
        }
        return baseCurrency.trim().toUpperCase(Locale.ROOT);
    }

    static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
