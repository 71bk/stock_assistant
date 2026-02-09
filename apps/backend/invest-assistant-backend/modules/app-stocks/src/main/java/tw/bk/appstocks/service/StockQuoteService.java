package tw.bk.appstocks.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tw.bk.appcommon.enums.AssetType;
import tw.bk.appcommon.enums.ErrorCode;
import tw.bk.appcommon.exception.BusinessException;
import tw.bk.appcommon.enums.MarketCode;
import tw.bk.appstocks.config.StockMarketProperties;
import tw.bk.appstocks.model.Candle;
import tw.bk.appstocks.model.Quote;
import tw.bk.appstocks.port.StockMarketClient;
import tw.bk.apppersistence.entity.InstrumentEntity;
import tw.bk.apppersistence.repository.InstrumentRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * 股票報價服務
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StockQuoteService {

    private final InstrumentRepository instrumentRepository;
    private final List<StockMarketClient> stockMarketClients;
    private final StockCacheService cacheService;
    private final StockMarketProperties properties;
    private final WarrantQuoteService warrantQuoteService;
    private final StockMetricsRecorder metricsRecorder;
    private Map<MarketCode, StockMarketClient> clientMap = Collections.emptyMap();
    private final ConcurrentHashMap<String, CompletableFuture<Object>> inflight = new ConcurrentHashMap<>();

    @PostConstruct
    void initClientMap() {
        clientMap = stockMarketClients.stream()
                .collect(Collectors.toUnmodifiableMap(
                        StockMarketClient::getSupportedMarket,
                        Function.identity(),
                        (a, b) -> {
                            throw new IllegalStateException("重複的市場 client: " + a.getSupportedMarket().getCode());
                        }));
    }

    /**
     * 取得股票即時報價
     * 
     * @param symbolKey 商品唯一識別碼（如 US:XNAS:AAPL）
     * @return 報價資料
     */
    public Quote getQuote(String symbolKey) {
        // 1. 檢查快取
        String cacheKey = "quote:" + symbolKey;
        Optional<Quote> cached = cacheService.get(cacheKey, Quote.class);
        if (cached.isPresent()) {
            metricsRecorder.recordCacheHit("quote");
            return cached.get();
        }
        metricsRecorder.recordCacheMiss("quote");

        return singleFlight(cacheKey, "quote", () -> {
            Optional<Quote> cachedAgain = cacheService.get(cacheKey, Quote.class);
            if (cachedAgain.isPresent()) {
                metricsRecorder.recordCacheHit("quote");
                return cachedAgain.get();
            }

            // 2. 查詢商品資訊
            InstrumentEntity instrument = instrumentRepository.findBySymbolKeyWithRelations(symbolKey)
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "商品不存在: " + symbolKey));
            if (AssetType.WARRANT.equals(instrument.getAssetTypeEnum())) {
                Quote quote = warrantQuoteService.getQuote(instrument.getTicker());
                cacheService.set(cacheKey, quote, properties.getCache().getQuoteTtl());
                return quote;
            }

            // 3. 根據市場選擇對應的 client
            String marketCodeValue = instrument.getMarket() != null ? instrument.getMarket().getCode() : null;
            MarketCode marketCode = MarketCode.requireSupported(
                    marketCodeValue,
                    ErrorCode.INTERNAL_ERROR,
                    "無法取得股票報價: " + marketCodeValue);
            StockMarketClient client = getClientByMarket(marketCode);

            // 4. 呼叫第三方 API
            Quote quote = client.getQuote(instrument.getTicker())
                    .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR,
                            "無法取得報價資料: " + symbolKey));

            // 5. 寫入快取
            cacheService.set(cacheKey, quote, properties.getCache().getQuoteTtl());
            return quote;
        });
    }

    public List<Candle> getCandles(String symbolKey, String interval, LocalDate from, LocalDate to) {
        // 1. 檢查快取
        String cacheKey = String.format("candles:%s:%s:%s:%s",
                symbolKey, interval, from, to);
        Optional<List<Candle>> cached = cacheService.getList(cacheKey, Candle.class);
        if (cached.isPresent()) {
            metricsRecorder.recordCacheHit("candles");
            return cached.get();
        }
        metricsRecorder.recordCacheMiss("candles");

        return singleFlight(cacheKey, "candles", () -> {
            Optional<List<Candle>> cachedAgain = cacheService.getList(cacheKey, Candle.class);
            if (cachedAgain.isPresent()) {
                metricsRecorder.recordCacheHit("candles");
                return cachedAgain.get();
            }

            // 2. 查詢商品資訊
            InstrumentEntity instrument = instrumentRepository.findBySymbolKeyWithRelations(symbolKey)
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "商品不存在: " + symbolKey));
            if (AssetType.WARRANT.equals(instrument.getAssetTypeEnum())) {
                List<Candle> candles = warrantQuoteService.getCandles(
                        instrument.getTicker(), interval, from, to);
                cacheService.setList(cacheKey, candles, properties.getCache().getCandlesTtl());
                return candles;
            }

            // 3. 根據市場選擇對應的 client
            String marketCodeValue = instrument.getMarket() != null ? instrument.getMarket().getCode() : null;
            MarketCode marketCode = MarketCode.requireSupported(
                    marketCodeValue,
                    ErrorCode.INTERNAL_ERROR,
                    "不支援的市場: " + marketCodeValue);
            StockMarketClient client = getClientByMarket(marketCode);

            // 4. 呼叫第三方 API
            List<Candle> candles = client.getCandles(instrument.getTicker(), interval, from, to);

            // 5. 寫入快取（空結果也快取避免重複請求）
            cacheService.setList(cacheKey, candles, properties.getCache().getCandlesTtl());
            return candles;
        });
    }

    private <T> T singleFlight(String key, String type, Supplier<T> supplier) {
        CompletableFuture<Object> future = new CompletableFuture<>();
        CompletableFuture<Object> existing = inflight.putIfAbsent(key, future);
        if (existing == null) {
            try {
                T value = supplier.get();
                future.complete(value);
                return value;
            } catch (Throwable ex) {
                future.completeExceptionally(ex);
                throw ex;
            } finally {
                inflight.remove(key, future);
            }
        }

        metricsRecorder.recordSingleFlightJoined(type);
        try {
            @SuppressWarnings("unchecked")
            T value = (T) existing.join();
            return value;
        } catch (CompletionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new RuntimeException(cause);
        }
    }

    private StockMarketClient getClientByMarket(MarketCode marketCode) {
        StockMarketClient client = clientMap.get(marketCode);
        if (client == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "不支援的市場: " + marketCode.getCode());
        }

        return client;
    }
}
