package tw.bk.appportfolio.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import tw.bk.appcommon.enums.TradeSide;
import tw.bk.appcommon.enums.TradeSource;

public record TradeView(
        Long id,
        Long instrumentId,
        LocalDate tradeDate,
        LocalDate settlementDate,
        TradeSide side,
        BigDecimal quantity,
        BigDecimal price,
        String currency,
        BigDecimal grossAmount,
        BigDecimal fee,
        BigDecimal tax,
        BigDecimal netAmount,
        TradeSource source,
        Long accountId) {
}
