package tw.bk.appai.service;

import java.util.LinkedHashSet;
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
    private static final Pattern SYMBOL_KEY_PATTERN = Pattern.compile("\\b([A-Za-z]{2}:[A-Za-z0-9]{4}:[A-Za-z0-9.\\-]{1,16})\\b");
    private static final Pattern URL_SYMBOL_KEY_PATTERN = Pattern
            .compile("(?i)(?:symbolKey|symbol_key)=([A-Za-z]{2}:[A-Za-z0-9]{4}:[A-Za-z0-9.\\-]{1,16})");
    private static final Pattern NUMERIC_TICKER_PATTERN = Pattern.compile("\\b(\\d{3,6})\\b");
    private static final Pattern LATIN_TOKEN_PATTERN = Pattern.compile("\\b([A-Za-z][A-Za-z0-9.\\-]{1,12})\\b");
    private static final Pattern HAN_TOKEN_PATTERN = Pattern.compile("(\\p{IsHan}{2,})");
    private static final Set<String> STOPWORDS = Set.of(
            "現在", "多少", "價格", "股價", "現價", "收盤", "漲跌", "報價",
            "今天", "今天的", "股票", "這隻", "這檔", "這支", "那隻", "那檔", "那支", "它",
            "請問", "幫我", "查一下", "查", "一下", "幫", "我", "請");
    private static final Set<String> LATIN_STOPWORDS = Set.of(
            "quote", "price", "stock", "stocks", "ticker", "symbol", "symbolkey");

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
            if (isSymbolKey(token)) {
                InstrumentEntity exact = instrumentService.findBySymbolKey(token).orElse(null);
                if (exact != null) {
                    List<InstrumentCandidate> results = List.of(toCandidate(exact));
                    if (cache != null) {
                        cache.put(cacheKey, results);
                    }
                    return results;
                }
                continue;
            }

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
        LinkedHashSet<String> tokens = new LinkedHashSet<>();
        collectUrlSymbolKeys(input, tokens);
        collectSymbolKeys(input, tokens);
        collectNumericTickers(input, tokens);
        collectLatinTokens(input, tokens);
        collectHanTokens(input, tokens);
        return tokens.stream().toList();
    }

    private String buildCacheKey(String input, int limit) {
        String normalized = input == null ? "" : input.trim().toLowerCase(Locale.ROOT);
        return normalized + "|" + limit;
    }

    private void collectUrlSymbolKeys(String input, LinkedHashSet<String> tokens) {
        Matcher matcher = URL_SYMBOL_KEY_PATTERN.matcher(input);
        while (matcher.find()) {
            String key = matcher.group(1);
            if (key == null || key.isBlank()) {
                continue;
            }
            tokens.add(key.trim().toUpperCase(Locale.ROOT));
        }
    }

    private void collectSymbolKeys(String input, LinkedHashSet<String> tokens) {
        Matcher matcher = SYMBOL_KEY_PATTERN.matcher(input);
        while (matcher.find()) {
            String key = matcher.group(1);
            if (key == null || key.isBlank()) {
                continue;
            }
            tokens.add(key.trim().toUpperCase(Locale.ROOT));
        }
    }

    private void collectNumericTickers(String input, LinkedHashSet<String> tokens) {
        Matcher matcher = NUMERIC_TICKER_PATTERN.matcher(input);
        while (matcher.find()) {
            String token = matcher.group(1);
            if (token == null || token.isBlank()) {
                continue;
            }
            tokens.add(token.trim());
        }
    }

    private void collectLatinTokens(String input, LinkedHashSet<String> tokens) {
        Matcher matcher = LATIN_TOKEN_PATTERN.matcher(input);
        while (matcher.find()) {
            String token = matcher.group(1);
            if (token == null || token.isBlank()) {
                continue;
            }
            String normalized = token.trim();
            if (normalized.contains(":")) {
                continue;
            }
            if (LATIN_STOPWORDS.contains(normalized.toLowerCase(Locale.ROOT))) {
                continue;
            }
            tokens.add(normalized);
        }
    }

    private void collectHanTokens(String input, LinkedHashSet<String> tokens) {
        String cleaned = input;
        for (String stopword : STOPWORDS) {
            cleaned = cleaned.replace(stopword, " ");
        }
        Matcher matcher = HAN_TOKEN_PATTERN.matcher(cleaned);
        while (matcher.find()) {
            String token = matcher.group(1);
            if (token == null) {
                continue;
            }
            String normalized = token.trim();
            if (normalized.length() < 2) {
                continue;
            }
            tokens.add(normalized);
        }
    }

    private boolean isSymbolKey(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        return SYMBOL_KEY_PATTERN.matcher(token.trim()).matches();
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
