package tw.bk.appapi.ai.skills;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import tw.bk.appai.model.ConversationMessageView;
import tw.bk.appai.model.InstrumentCandidate;
import tw.bk.appai.model.QuoteCandidate;
import tw.bk.appai.service.AiConversationService;
import tw.bk.appai.service.AiInstrumentToolService;
import tw.bk.appai.service.AiQuoteToolService;
import tw.bk.appai.skill.ChatSkill;
import tw.bk.appai.skill.ChatSkillContext;
import tw.bk.appai.skill.ChatSkillErrorCode;
import tw.bk.appai.skill.ChatSkillResult;
import tw.bk.appai.skill.ChatSkillSpec;
import tw.bk.appcommon.enums.UserRole;

@Service
@Order(10)
public class MarketQuoteSkill implements ChatSkill {
    private static final String LAST_MENTIONED_CACHE_NAME = "conversationLastMentioned";
    private static final Pattern SYMBOL_KEY_PATTERN = Pattern
            .compile("\\b([A-Za-z]{2}:[A-Za-z0-9]{4}:[A-Za-z0-9.\\-]{1,16})\\b");
    private static final Pattern URL_SYMBOL_KEY_PATTERN = Pattern
            .compile("(?i)(?:symbolKey|symbol_key)=([A-Za-z]{2}:[A-Za-z0-9]{4}:[A-Za-z0-9.\\-]{1,16})");
    private static final List<String> PRONOUN_MARKERS = List.of(
            "這隻", "這檔", "這支", "那隻", "那檔", "那支", "它", "該股",
            "他", "她", "這個", "那個");
    private static final String QUOTE_KEYWORDS_CHINESE = "價格,股價,多少,現價,收盤,漲跌,報價,最新,標價";

    private final AiConversationService conversationService;
    private final AiInstrumentToolService instrumentToolService;
    private final AiQuoteToolService quoteToolService;
    private final CacheManager cacheManager;

    @Value("${app.ai.chat.skills.market-quote.enabled:true}")
    private boolean skillEnabled;

    @Value("${app.ai.chat.skills.market-quote.timeout-ms:600}")
    private long skillTimeoutMs;

    @Value("${app.ai.chat.instrument-search.enabled:true}")
    private boolean instrumentSearchEnabled;

    @Value("${app.ai.chat.instrument-search.limit:10}")
    private int instrumentSearchLimit;

    @Value("${app.ai.chat.quote-search.enabled:true}")
    private boolean quoteSearchEnabled;

    @Value("${app.ai.chat.quote-search.keywords-en:quote,price}")
    private String quoteSearchKeywordsEn;

    @Value("${app.ai.chat.pronoun-lookback.limit:5}")
    private int pronounLookbackLimit;

    public MarketQuoteSkill(
            AiConversationService conversationService,
            AiInstrumentToolService instrumentToolService,
            AiQuoteToolService quoteToolService,
            CacheManager cacheManager) {
        this.conversationService = conversationService;
        this.instrumentToolService = instrumentToolService;
        this.quoteToolService = quoteToolService;
        this.cacheManager = cacheManager;
    }

    @Override
    public ChatSkillSpec spec() {
        return new ChatSkillSpec(
                "market-quote-skill",
                "1.0.0",
                UserRole.USER,
                skillTimeoutMs,
                skillEnabled,
                "{\"type\":\"object\",\"required\":[\"userId\",\"conversationId\",\"content\"]}",
                "{\"type\":\"object\",\"description\":\"instrument_candidates + quote block\"}");
    }

    @Override
    public boolean supports(ChatSkillContext context) {
        if (!skillEnabled || !instrumentSearchEnabled || context == null) {
            return false;
        }
        return context.content() != null && !context.content().isBlank();
    }

    @Override
    public ChatSkillResult execute(ChatSkillContext context) {
        long started = System.currentTimeMillis();
        if (context.userId() == null || context.conversationId() == null) {
            return ChatSkillResult.error(spec().name(), ChatSkillErrorCode.SKILL_BAD_INPUT,
                    "userId and conversationId are required", elapsedMs(started));
        }
        String built = buildInstrumentContext(context.userId(), context.conversationId(), context.currentUserMessageId(),
                context.content());
        if (built == null || built.isBlank()) {
            return ChatSkillResult.miss(spec().name(), elapsedMs(started));
        }
        return ChatSkillResult.hit(spec().name(), built, elapsedMs(started));
    }

    private String buildInstrumentContext(Long userId, Long conversationId, Long currentUserMessageId, String content) {
        if (content == null || content.isBlank()) {
            return null;
        }
        int limit = Math.min(Math.max(instrumentSearchLimit, 1), 20);
        boolean pronounQuery = containsPronoun(content);
        boolean fetchQuote = shouldFetchQuote(content);
        boolean quoteIntent = quoteSearchEnabled && fetchQuote;
        List<InstrumentCandidate> candidates = resolveCandidates(content, limit);
        if ((candidates == null || candidates.isEmpty()) && pronounQuery) {
            candidates = resolveCandidatesFromConversation(userId, conversationId, currentUserMessageId, limit);
        } else if ((candidates == null || candidates.isEmpty()) && quoteIntent) {
            candidates = loadLastMentionedSymbolKey(userId, conversationId);
        }
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        rememberLastMentionedSymbolKey(userId, conversationId, candidates.get(0));

        StringBuilder sb = new StringBuilder();
        sb.append("instrument_candidates:\n");
        for (InstrumentCandidate candidate : candidates) {
            if (candidate == null) {
                continue;
            }
            sb.append("- ");
            if (candidate.ticker() != null) {
                sb.append(candidate.ticker());
            }
            if (candidate.name() != null && !candidate.name().isBlank()) {
                sb.append(" ").append(candidate.name());
            }
            if (candidate.symbolKey() != null && !candidate.symbolKey().isBlank()) {
                sb.append(" (").append(candidate.symbolKey()).append(")");
            }
            if (candidate.assetType() != null && !candidate.assetType().isBlank()) {
                sb.append(" type=").append(candidate.assetType());
            }
            sb.append('\n');
        }

        if (quoteIntent) {
            InstrumentCandidate first = candidates.get(0);
            QuoteCandidate quote = null;
            String quoteError = null;
            try {
                quote = quoteToolService.getQuote(first.symbolKey());
            } catch (Exception ex) {
                quoteError = ex.getMessage();
            }
            if (quote != null) {
                sb.append("quote:\n");
                appendQuote(sb, quote);
                sb.append("tool_quote_available: true\n");
            } else {
                sb.append("tool_quote_available: false\n");
                if (quoteError == null || quoteError.isBlank()) {
                    quoteError = "QUOTE_NOT_AVAILABLE";
                }
                sb.append("tool_quote_error: ").append(sanitizeToolValue(quoteError)).append('\n');
            }
        }
        return sb.toString().trim();
    }

    private List<InstrumentCandidate> resolveCandidates(String content, int limit) {
        List<InstrumentCandidate> candidates = instrumentToolService.searchCandidates(content, limit);
        if (candidates != null && !candidates.isEmpty()) {
            return candidates;
        }

        String symbolKey = extractSymbolKey(content);
        if (symbolKey == null) {
            return List.of();
        }
        return instrumentToolService.searchCandidates(symbolKey, 1);
    }

    private List<InstrumentCandidate> resolveCandidatesFromConversation(
            Long userId,
            Long conversationId,
            Long currentUserMessageId,
            int limit) {
        List<InstrumentCandidate> cached = loadLastMentionedSymbolKey(userId, conversationId);
        if (!cached.isEmpty()) {
            return cached;
        }

        int lookback = Math.min(Math.max(pronounLookbackLimit, 1), 10);
        List<ConversationMessageView> recentMessages = conversationService.getRecentMessages(userId, conversationId,
                lookback + 2);
        for (int i = recentMessages.size() - 1; i >= 0; i--) {
            ConversationMessageView message = recentMessages.get(i);
            if (message == null) {
                continue;
            }
            if (currentUserMessageId != null && currentUserMessageId.equals(message.id())) {
                continue;
            }
            if (message.role() == null || !"user".equalsIgnoreCase(message.role().value())) {
                continue;
            }
            List<InstrumentCandidate> candidates = resolveCandidates(message.content(), limit);
            if (candidates != null && !candidates.isEmpty()) {
                rememberLastMentionedSymbolKey(userId, conversationId, candidates.get(0));
                return candidates;
            }
        }
        return List.of();
    }

    private boolean containsPronoun(String content) {
        if (content == null || content.isBlank()) {
            return false;
        }
        String compact = content.replace(" ", "");
        for (String marker : PRONOUN_MARKERS) {
            if (compact.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    private String extractSymbolKey(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }
        Matcher urlMatcher = URL_SYMBOL_KEY_PATTERN.matcher(content);
        if (urlMatcher.find()) {
            String key = urlMatcher.group(1);
            return key == null ? null : key.trim().toUpperCase(Locale.ROOT);
        }

        Matcher matcher = SYMBOL_KEY_PATTERN.matcher(content);
        if (!matcher.find()) {
            return null;
        }
        String key = matcher.group(1);
        return key == null ? null : key.trim().toUpperCase(Locale.ROOT);
    }

    private void rememberLastMentionedSymbolKey(Long userId, Long conversationId, InstrumentCandidate candidate) {
        if (candidate == null || candidate.symbolKey() == null || candidate.symbolKey().isBlank()) {
            return;
        }
        Cache cache = cacheManager.getCache(LAST_MENTIONED_CACHE_NAME);
        if (cache == null) {
            return;
        }
        cache.put(lastMentionedCacheKey(userId, conversationId), candidate.symbolKey().trim().toUpperCase(Locale.ROOT));
    }

    private List<InstrumentCandidate> loadLastMentionedSymbolKey(Long userId, Long conversationId) {
        Cache cache = cacheManager.getCache(LAST_MENTIONED_CACHE_NAME);
        if (cache == null) {
            return List.of();
        }
        String symbolKey = cache.get(lastMentionedCacheKey(userId, conversationId), String.class);
        if (symbolKey == null || symbolKey.isBlank()) {
            return List.of();
        }
        List<InstrumentCandidate> candidates = instrumentToolService.searchCandidates(symbolKey, 1);
        if (candidates == null || candidates.isEmpty()) {
            cache.evict(lastMentionedCacheKey(userId, conversationId));
            return List.of();
        }
        return candidates;
    }

    private String lastMentionedCacheKey(Long userId, Long conversationId) {
        return userId + ":" + conversationId;
    }

    private String sanitizeToolValue(String value) {
        if (value == null) {
            return "";
        }
        return value.replace('\n', ' ').replace('\r', ' ').trim();
    }

    private void appendQuote(StringBuilder sb, QuoteCandidate quote) {
        sb.append("- symbol_key: ").append(quote.symbolKey()).append('\n');
        if (quote.ticker() != null) {
            sb.append("  ticker: ").append(quote.ticker()).append('\n');
        }
        if (quote.price() != null) {
            sb.append("  price: ").append(quote.price()).append('\n');
        }
        if (quote.change() != null) {
            sb.append("  change: ").append(quote.change()).append('\n');
        }
        if (quote.changePercent() != null) {
            sb.append("  change_pct: ").append(quote.changePercent()).append('\n');
        }
        if (quote.open() != null) {
            sb.append("  open: ").append(quote.open()).append('\n');
        }
        if (quote.high() != null) {
            sb.append("  high: ").append(quote.high()).append('\n');
        }
        if (quote.low() != null) {
            sb.append("  low: ").append(quote.low()).append('\n');
        }
        if (quote.previousClose() != null) {
            sb.append("  previous_close: ").append(quote.previousClose()).append('\n');
        }
        if (quote.volume() != null) {
            sb.append("  volume: ").append(quote.volume()).append('\n');
        }
        if (quote.timestamp() != null) {
            sb.append("  ts_utc: ").append(quote.timestamp()).append('\n');
        }
    }

    private boolean shouldFetchQuote(String content) {
        if (content == null || content.isBlank()) {
            return false;
        }
        String lowered = content.toLowerCase(Locale.ROOT);
        String allKeywords = QUOTE_KEYWORDS_CHINESE + "," + quoteSearchKeywordsEn;
        String[] keywords = allKeywords.split(",");
        for (String keyword : keywords) {
            String trimmed = keyword.trim().toLowerCase(Locale.ROOT);
            if (trimmed.isEmpty()) {
                continue;
            }
            if (lowered.contains(trimmed)) {
                return true;
            }
        }
        return false;
    }

    private long elapsedMs(long started) {
        return Math.max(0L, System.currentTimeMillis() - started);
    }
}

