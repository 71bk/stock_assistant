package tw.bk.appocr.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import tw.bk.appportfolio.model.TradeSide;

public record OcrDraftUpdate(
                Long instrumentId,
                String rawTicker,
                String name,
                LocalDate tradeDate,
                LocalDate settlementDate,
                TradeSide side,
                BigDecimal quantity,
                BigDecimal price,
                String currency,
                BigDecimal fee,
                BigDecimal tax) {
}
