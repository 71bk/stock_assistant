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
 * Fugle API 客戶端（台股）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FugleClient implements StockMarketClient {

    private final StockMarketProperties properties;
    private final RestClient restClient = RestClient.create();

    @Override
    public Optional<Quote> getQuote(String ticker) {
        try {
            String url = String.format("%s/intraday/quote?symbolId=%s&apiToken=%s",
                    properties.getFugle().getBaseUrl(),
                    ticker,
                    properties.getFugle().getApiKey());

            JsonNode response = restClient.get()
                    .uri(url)
                    .retrieve()
                    .body(JsonNode.class);

            if (response == null || !response.has("data")) {
                log.warn("Fugle returned empty response for ticker: {}", ticker);
                return Optional.empty();
            }

            JsonNode data = response.get("data");
            JsonNode info = data.get("info");
            JsonNode priceHighLow = data.has("priceHighLow") ? data.get("priceHighLow") : null;

            BigDecimal lastPrice = parseBigDecimal(info.get("lastPrice"));
            BigDecimal referencePrice = parseBigDecimal(info.get("referencePrice"));
            BigDecimal change = lastPrice.subtract(referencePrice);

            return Optional.of(Quote.builder()
                    .ticker(ticker)
                    .price(lastPrice)
                    .open(parseBigDecimal(priceHighLow != null ? priceHighLow.get("open") : null))
                    .high(parseBigDecimal(priceHighLow != null ? priceHighLow.get("high") : null))
                    .low(parseBigDecimal(priceHighLow != null ? priceHighLow.get("low") : null))
                    .previousClose(referencePrice)
                    .volume(parseLong(info.get("totalVolumeShares")))
                    .change(change)
                    .changePercent(parseBigDecimal(info.get("changePercent")))
                    .timestamp(Instant.now())
                    .build());

        } catch (Exception e) {
            log.error("Failed to fetch quote from Fugle for ticker: {}", ticker, e);
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
        return MarketCode.TW;
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
}
