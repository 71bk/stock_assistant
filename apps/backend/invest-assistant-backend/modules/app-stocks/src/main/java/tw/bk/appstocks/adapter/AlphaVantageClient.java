package tw.bk.appstocks.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tw.bk.appcommon.model.MarketCode;
import tw.bk.appstocks.config.StockMarketProperties;
import tw.bk.appstocks.model.Candle;
import tw.bk.appstocks.model.Quote;
import tw.bk.appstocks.port.StockMarketClient;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Alpha Vantage API 客戶端（美股）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlphaVantageClient implements StockMarketClient {

    private final StockMarketProperties properties;
    private final RestClient restClient = RestClient.create();

    @Override
    public Optional<Quote> getQuote(String ticker) {
        try {
            String url = String.format("%s?function=GLOBAL_QUOTE&symbol=%s&apikey=%s",
                    properties.getAlphaVantage().getBaseUrl(),
                    ticker,
                    properties.getAlphaVantage().getApiKey());

            JsonNode response = restClient.get()
                    .uri(url)
                    .retrieve()
                    .body(JsonNode.class);

            if (response == null || !response.has("Global Quote")) {
                log.warn("Alpha Vantage returned empty response for ticker: {}", ticker);
                return Optional.empty();
            }

            JsonNode quote = response.get("Global Quote");

            return Optional.of(Quote.builder()
                    .ticker(ticker)
                    .price(parseBigDecimal(quote.get("05. price")))
                    .open(parseBigDecimal(quote.get("02. open")))
                    .high(parseBigDecimal(quote.get("03. high")))
                    .low(parseBigDecimal(quote.get("04. low")))
                    .previousClose(parseBigDecimal(quote.get("08. previous close")))
                    .volume(parseLong(quote.get("06. volume")))
                    .change(parseBigDecimal(quote.get("09. change")))
                    .changePercent(parseChangePercent(quote.get("10. change percent")))
                    .timestamp(Instant.now())
                    .build());

        } catch (Exception e) {
            log.error("Failed to fetch quote from Alpha Vantage for ticker: {}", ticker, e);
            return Optional.empty();
        }
    }

    @Override
    public List<Candle> getCandles(String ticker, String interval, LocalDate from, LocalDate to) {
        // TODO: 實作 K 線查詢（MVP 可後補）
        return List.of();
    }

    @Override
    public MarketCode getSupportedMarket() {
        return MarketCode.US;
    }

    private BigDecimal parseBigDecimal(JsonNode node) {
        if (node == null || node.isNull()) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(node.asText());
    }

    private Long parseLong(JsonNode node) {
        if (node == null || node.isNull()) {
            return 0L;
        }
        return node.asLong();
    }

    private BigDecimal parseChangePercent(JsonNode node) {
        if (node == null || node.isNull()) {
            return BigDecimal.ZERO;
        }
        // 移除 "%" 符號
        String text = node.asText().replace("%", "");
        return new BigDecimal(text);
    }
}
