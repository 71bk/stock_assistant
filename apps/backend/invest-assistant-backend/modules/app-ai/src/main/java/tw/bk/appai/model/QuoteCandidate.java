package tw.bk.appai.model;

import java.time.Instant;

public record QuoteCandidate(
        String symbolKey,
        String ticker,
        String price,
        String change,
        String changePercent,
        String open,
        String high,
        String low,
        String previousClose,
        Long volume,
        Instant timestamp) {
}
