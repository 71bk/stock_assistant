package tw.bk.appocr.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * AI Worker OCR 回傳的單筆交易資料。
 */
public record AiWorkerParsedTrade(
        @JsonProperty("ticker") String ticker,
        @JsonProperty("side") String side,
        @JsonProperty("quantity") BigDecimal quantity,
        @JsonProperty("price") BigDecimal price,
        @JsonProperty("trade_date") LocalDate tradeDate,
        @JsonProperty("settlement_date") LocalDate settlementDate,
        @JsonProperty("currency") String currency,
        @JsonProperty("fee") BigDecimal fee,
        @JsonProperty("tax") BigDecimal tax,
        @JsonProperty("stock_name") String stockName,
        @JsonProperty("confidence") BigDecimal confidence,
        @JsonProperty("warnings") List<String> warnings,
        @JsonProperty("raw_line") String rawLine) {
}
