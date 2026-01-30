package tw.bk.appportfolio.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TradeCommand(
                Long instrumentId,
                LocalDate tradeDate,
                LocalDate settlementDate,
                TradeSide side,
                BigDecimal quantity,
                BigDecimal price,
                String currency,
                BigDecimal fee,
                BigDecimal tax,
                Long accountId,
                String source) {
}
