package tw.bk.appstocks.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.format.DateTimeParseException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;
import tw.bk.appcommon.error.ErrorCode;
import tw.bk.appcommon.exception.BusinessException;
import tw.bk.appcommon.model.MarketCode;
import tw.bk.appstocks.adapter.dto.FugleCandlesResponse;
import tw.bk.appstocks.adapter.dto.FugleQuoteResponse;
import tw.bk.appstocks.config.StockMarketProperties;
import tw.bk.appstocks.model.Candle;
import tw.bk.appstocks.model.Quote;
import tw.bk.appstocks.model.TickerItem;
import tw.bk.appstocks.model.TickerList;
import tw.bk.appstocks.model.TickerQuery;
import tw.bk.appstocks.port.StockMarketClient;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Fugle API 客戶端（台股）
 * 使用 DTO 避免 Jackson 2 vs 3 衝突
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FugleClient implements StockMarketClient {

    private final StockMarketProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient = RestClient.create();
    private static final String HEADER_API_KEY = "X-API-KEY";

    @Override
    public Optional<Quote> getQuote(String ticker) {
        try {
            String url = buildUrl("/intraday/quote", ticker);

            FugleQuoteResponse response = withApiKey(restClient.get().uri(url))
                    .retrieve()
                    .body(FugleQuoteResponse.class);

            if (response == null) {
                log.warn("Fugle quote response is null for ticker: {}", ticker);
                return Optional.empty();
            }

            BigDecimal lastPrice = response.getLastPrice() != null ? response.getLastPrice() : BigDecimal.ZERO;
            BigDecimal referencePrice = response.getReferencePrice() != null ? response.getReferencePrice()
                    : BigDecimal.ZERO;
            BigDecimal change = response.getChange() != null ? response.getChange()
                    : lastPrice.subtract(referencePrice);

            return Optional.of(Quote.builder()
                    .ticker(ticker)
                    .price(lastPrice)
                    .open(response.getOpenPrice())
                    .high(response.getHighPrice())
                    .low(response.getLowPrice())
                    .previousClose(response.getPreviousClose())
                    .volume(response.getTotal() != null ? response.getTotal().getTradeVolume() : null)
                    .change(change)
                    .changePercent(response.getChangePercent())
                    .timestamp(Instant.now())
                    .build());

        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 429) {
                throw rateLimited("Fugle", ticker, ex.getResponseHeaders());
            }
            log.error("Failed to fetch quote from Fugle for ticker: {}", ticker, ex);
            return Optional.empty();
        } catch (Exception e) {
            log.error("Failed to fetch quote from Fugle for ticker: {}", ticker, e);
            return Optional.empty();
        }
    }

    @Override
    public List<Candle> getCandles(String ticker, String interval, LocalDate from, LocalDate to) {
        try {
            String path = shouldUseHistorical(interval, from, to)
                    ? "/historical/candles"
                    : "/intraday/candles";
            String url = buildUrl(path, ticker);

            FugleCandlesResponse response = withApiKey(restClient.get().uri(url))
                    .retrieve()
                    .body(FugleCandlesResponse.class);

            if (response == null || response.getData() == null) {
                log.warn("Fugle returned empty candles for ticker: {}", ticker);
                return List.of();
            }

            List<Candle> candles = new ArrayList<>();
            for (FugleCandlesResponse.CandleData candleData : response.getData()) {
                LocalDateTime timestamp = parseDate(candleData.getDate());
                if (timestamp == null || !isWithinRange(timestamp, from, to)) {
                    continue;
                }

                Candle candle = Candle.builder()
                        .ticker(ticker)
                        .timestamp(timestamp)
                        .open(candleData.getOpen() != null ? candleData.getOpen() : BigDecimal.ZERO)
                        .high(candleData.getHigh() != null ? candleData.getHigh() : BigDecimal.ZERO)
                        .low(candleData.getLow() != null ? candleData.getLow() : BigDecimal.ZERO)
                        .close(candleData.getClose() != null ? candleData.getClose() : BigDecimal.ZERO)
                        .volume(candleData.getVolume() != null ? candleData.getVolume() : 0L)
                        .build();
                candles.add(candle);
            }

            candles.sort(Comparator.comparing(Candle::getTimestamp));
            return candles;
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 429) {
                throw rateLimited("Fugle", ticker, ex.getResponseHeaders());
            }
            log.error("Failed to fetch candles from Fugle for ticker: {}", ticker, ex);
            return List.of();
        } catch (Exception e) {
            log.error("Failed to fetch candles from Fugle for ticker: {}", ticker, e);
            return List.of();
        }
    }

    @Override
    public Optional<TickerList> getTickers(TickerQuery query) {
        try {
            String baseUrl = buildUrl("/intraday/tickers");
            UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(baseUrl);

            if (query != null) {
                addQueryParam(builder, "type", query.getType());
                addQueryParam(builder, "exchange", query.getExchange());
                addQueryParam(builder, "market", query.getMarket());
                addQueryParam(builder, "industry", query.getIndustry());
                addQueryParam(builder, "isNormal", toFlag(query.getIsNormal()));
                addQueryParam(builder, "isAttention", toFlag(query.getIsAttention()));
                addQueryParam(builder, "isDisposition", toFlag(query.getIsDisposition()));
                addQueryParam(builder, "isHalted", toFlag(query.getIsHalted()));
            }

            String url = builder.build(true).toUriString();
            String body = withApiKey(restClient.get().uri(url))
                    .retrieve()
                    .body(String.class);

            if (body == null || body.isBlank()) {
                return Optional.empty();
            }

            JsonNode root = objectMapper.readTree(body);
            TickerList list = parseTickerList(root, query);
            return Optional.ofNullable(list);
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 429) {
                throw rateLimited("Fugle", "tickers", ex.getResponseHeaders());
            }
            log.error("Failed to fetch tickers from Fugle", ex);
            return Optional.empty();
        } catch (IOException ex) {
            log.error("Failed to parse Fugle tickers response", ex);
            return Optional.empty();
        } catch (Exception ex) {
            log.error("Failed to fetch tickers from Fugle", ex);
            return Optional.empty();
        }
    }

    @Override
    public MarketCode getSupportedMarket() {
        return MarketCode.TW;
    }

    private boolean shouldUseHistorical(String interval, LocalDate from, LocalDate to) {
        if (interval != null) {
            String normalized = interval.trim().toLowerCase();
            if (normalized.equals("1d") || normalized.equals("1w") || normalized.equals("1mo")) {
                return true;
            }
        }
        LocalDate today = LocalDate.now();
        if (from != null && from.isBefore(today.minusDays(1))) {
            return true;
        }
        return to != null && to.isBefore(today);
    }

    private boolean isWithinRange(LocalDateTime timestamp, LocalDate from, LocalDate to) {
        LocalDate date = timestamp.toLocalDate();
        if (from != null && date.isBefore(from)) {
            return false;
        }
        return to == null || !date.isAfter(to);
    }

    private LocalDateTime parseDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(dateStr).atStartOfDay();
        } catch (Exception e) {
            log.warn("Failed to parse date: {}", dateStr);
            return null;
        }
    }

    private String buildUrl(String path, String ticker) {
        String base = properties.getFugle().getBaseUrl();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return String.format("%s%s/%s", base, path, ticker);
    }

    private String buildUrl(String path) {
        String base = properties.getFugle().getBaseUrl();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return String.format("%s%s", base, path);
    }

    private RestClient.RequestHeadersSpec<?> withApiKey(RestClient.RequestHeadersSpec<?> spec) {
        String apiKey = properties.getFugle().getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            return spec;
        }
        return spec.header(HEADER_API_KEY, apiKey.trim());
    }

    private void addQueryParam(UriComponentsBuilder builder, String name, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        builder.queryParam(name, value.trim());
    }

    private String toFlag(Boolean value) {
        if (value == null) {
            return null;
        }
        return value ? "1" : "0";
    }

    private TickerList parseTickerList(JsonNode root, TickerQuery query) {
        if (root == null || root.isNull()) {
            return null;
        }

        List<TickerItem> items = extractItems(root).stream()
                .map(this::parseTickerItem)
                .filter(item -> item.getSymbol() != null && !item.getSymbol().isBlank())
                .collect(java.util.stream.Collectors.toList());

        String market = readText(root, "market");
        if (market == null && query != null) {
            market = query.getMarket();
        }

        return TickerList.builder()
                .date(readDate(root, "date"))
                .type(readText(root, "type", query != null ? query.getType() : null))
                .exchange(readText(root, "exchange", query != null ? query.getExchange() : null))
                .market(market)
                .industry(readText(root, "industry", query != null ? query.getIndustry() : null))
                .isNormal(readBoolean(root, "isNormal", query != null ? query.getIsNormal() : null))
                .isAttention(readBoolean(root, "isAttention", query != null ? query.getIsAttention() : null))
                .isDisposition(readBoolean(root, "isDisposition", query != null ? query.getIsDisposition() : null))
                .isHalted(readBoolean(root, "isHalted", query != null ? query.getIsHalted() : null))
                .data(items)
                .build();
    }

    private List<JsonNode> extractItems(JsonNode root) {
        if (root.has("data")) {
            JsonNode dataNode = root.get("data");
            if (dataNode.isArray()) {
                return toList(dataNode);
            }
            if (dataNode.isObject() && dataNode.has("data") && dataNode.get("data").isArray()) {
                return toList(dataNode.get("data"));
            }
        }
        if (root.has("items") && root.get("items").isArray()) {
            return toList(root.get("items"));
        }
        for (JsonNode field : root) {
            if (field.isArray()) {
                return toList(field);
            }
        }
        return List.of();
    }

    private List<JsonNode> toList(JsonNode arrayNode) {
        List<JsonNode> list = new java.util.ArrayList<>();
        arrayNode.forEach(list::add);
        return list;
    }

    private TickerItem parseTickerItem(JsonNode node) {
        String symbol = readText(node, "symbol", readText(node, "ticker", readText(node, "code", null)));
        String name = readText(node, "name",
                readText(node, "nameZh",
                        readText(node, "nameEn",
                                readText(node, "cname",
                                        readText(node, "ename", symbol)))));
        return TickerItem.builder()
                .symbol(symbol)
                .name(name)
                .build();
    }

    private String readText(JsonNode node, String field) {
        return readText(node, field, null);
    }

    private String readText(JsonNode node, String field, String fallback) {
        if (node == null || field == null) {
            return fallback;
        }
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return fallback;
        }
        String text = value.asText();
        return text == null || text.isBlank() ? fallback : text;
    }

    private Boolean readBoolean(JsonNode node, String field, Boolean fallback) {
        if (node == null || field == null) {
            return fallback;
        }
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return fallback;
        }
        if (value.isBoolean()) {
            return value.asBoolean();
        }
        if (value.isNumber()) {
            return value.asInt() != 0;
        }
        if (value.isTextual()) {
            String text = value.asText().trim().toLowerCase();
            if ("1".equals(text) || "true".equals(text)) {
                return true;
            }
            if ("0".equals(text) || "false".equals(text)) {
                return false;
            }
        }
        return fallback;
    }

    private LocalDate readDate(JsonNode node, String field) {
        String text = readText(node, field);
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(text);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private BusinessException rateLimited(String vendor, String ticker, HttpHeaders headers) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("vendor", vendor);
        details.put("ticker", ticker);
        if (headers != null) {
            String retryAfter = headers.getFirst(HttpHeaders.RETRY_AFTER);
            if (retryAfter != null && !retryAfter.isBlank()) {
                details.put("retryAfter", retryAfter);
            }
        }
        return new BusinessException(ErrorCode.RATE_LIMITED,
                "行情供應商限流，請稍後再試。",
                details);
    }
}
