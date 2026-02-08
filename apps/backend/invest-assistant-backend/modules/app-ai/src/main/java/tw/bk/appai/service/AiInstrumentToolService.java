package tw.bk.appai.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import tw.bk.appai.model.InstrumentCandidate;
import tw.bk.appstocks.service.InstrumentService;
import tw.bk.apppersistence.entity.ExchangeEntity;
import tw.bk.apppersistence.entity.InstrumentEntity;
import tw.bk.apppersistence.entity.MarketEntity;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiInstrumentToolService {
    private static final Pattern TOKEN_PATTERN = Pattern.compile("(\\p{IsHan}{2,}|[A-Za-z]{2,}|\\d{3,6})");
    private static final Set<String> STOPWORDS = Set.of(
            "現在", "多少", "價格", "股價", "今天", "今天的", "股票", "ETF", "請問", "幫我", "查一下");

    private final InstrumentService instrumentService;
    private final CacheManager cacheManager;

    public List<InstrumentCandidate> searchCandidates(String input, int limit) {
        if (input == null || input.isBlank()) {
            return List.of();
        }

        String normalizedInput = input.trim();
        String cacheKey = buildCacheKey(normalizedInput, limit);
        Cache cache = cacheManager.getCache("instrumentSearch");
        if (cache != null) {
            Cache.ValueWrapper cached = cache.get(cacheKey);
            if (cached != null) {
                @SuppressWarnings("unchecked")
                List<InstrumentCandidate> cachedList = (List<InstrumentCandidate>) cached.get();
                if (cachedList != null) {
                    log.debug("Instrument search cache hit: key={}", cacheKey);
                    return cachedList;
                }
            }
            log.debug("Instrument search cache miss: key={}", cacheKey);
        }

        List<String> tokens = extractTokens(input);
        for (String token : tokens) {
            List<InstrumentEntity> matches = instrumentService.searchInstruments(token, limit);
            if (!matches.isEmpty()) {
                List<InstrumentCandidate> results = matches.stream()
                        .map(this::toCandidate)
                        .toList();
                if (cache != null && !results.isEmpty()) {
                    cache.put(cacheKey, results);
                }
                return results;
            }
        }
        return List.of();
    }

    private List<String> extractTokens(String input) {
        List<String> tokens = new ArrayList<>();
        Matcher matcher = TOKEN_PATTERN.matcher(input);
        while (matcher.find()) {
            String token = matcher.group(1);
            if (token == null) {
                continue;
            }
            String normalized = token.trim();
            if (normalized.isEmpty()) {
                continue;
            }
            if (STOPWORDS.contains(normalized)) {
                continue;
            }
            tokens.add(normalized);
        }
        return tokens.stream()
                .distinct()
                .sorted(Comparator.comparingInt(String::length).reversed())
                .toList();
    }

    private String buildCacheKey(String input, int limit) {
        String normalized = input == null ? "" : input.trim().toLowerCase(Locale.ROOT);
        return normalized + "|" + limit;
    }

    private InstrumentCandidate toCandidate(InstrumentEntity entity) {
        if (entity == null) {
            return null;
        }
        String name = firstNonBlank(entity.getNameZh(), entity.getNameEn());
        MarketEntity market = entity.getMarket();
        ExchangeEntity exchange = entity.getExchange();
        return new InstrumentCandidate(
                entity.getSymbolKey(),
                entity.getTicker(),
                name,
                market != null ? market.getCode() : null,
                exchange != null ? exchange.getCode() : null,
                entity.getAssetType());
    }

    private String firstNonBlank(String primary, String fallback) {
        if (primary != null && !primary.isBlank()) {
            return primary.trim();
        }
        if (fallback != null && !fallback.isBlank()) {
            return fallback.trim();
        }
        return null;
    }
}
