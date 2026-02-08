package tw.bk.appai.service;

import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tw.bk.appai.model.QuoteCandidate;
import tw.bk.appstocks.model.Quote;
import tw.bk.appstocks.service.StockQuoteService;

@Service
@RequiredArgsConstructor
public class AiQuoteToolService {
    private final StockQuoteService stockQuoteService;

    public QuoteCandidate getQuote(String symbolKey) {
        if (symbolKey == null || symbolKey.isBlank()) {
            return null;
        }
        Quote quote = stockQuoteService.getQuote(symbolKey);
        if (quote == null) {
            return null;
        }
        return new QuoteCandidate(
                symbolKey,
                quote.getTicker(),
                toPlain(quote.getPrice()),
                toPlain(quote.getChange()),
                toPlain(quote.getChangePercent()),
                toPlain(quote.getOpen()),
                toPlain(quote.getHigh()),
                toPlain(quote.getLow()),
                toPlain(quote.getPreviousClose()),
                quote.getVolume(),
                quote.getTimestamp());
    }

    private String toPlain(BigDecimal value) {
        if (value == null) {
            return null;
        }
        return value.stripTrailingZeros().toPlainString();
    }
}
