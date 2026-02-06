package tw.bk.appapi.stocks.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tw.bk.appstocks.model.Candle;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Collectors;

/**
 * K線資料回應
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CandleResponse {
    private OffsetDateTime timestamp;
    private BigDecimal open;
    private BigDecimal high;
    private BigDecimal low;
    private BigDecimal close;
    private Long volume;

    public static CandleResponse from(Candle candle) {
        return CandleResponse.builder()
                .timestamp(candle.getTimestamp() == null ? null : candle.getTimestamp().atOffset(ZoneOffset.ofHours(8)))
                .open(candle.getOpen())
                .high(candle.getHigh())
                .low(candle.getLow())
                .close(candle.getClose())
                .volume(candle.getVolume())
                .build();
    }

    public static List<CandleResponse> fromList(List<Candle> candles) {
        return candles.stream()
                .map(CandleResponse::from)
                .collect(Collectors.toList());
    }
}
