package tw.bk.appapi.ai.skills;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import tw.bk.appai.skill.ChatSkill;
import tw.bk.appai.skill.ChatSkillContext;
import tw.bk.appai.skill.ChatSkillErrorCode;
import tw.bk.appai.skill.ChatSkillResult;
import tw.bk.appai.skill.ChatSkillSpec;
import tw.bk.appcommon.enums.UserRole;
import tw.bk.appportfolio.model.PortfolioChatContext;
import tw.bk.appportfolio.service.PortfolioService;
import tw.bk.appportfolio.service.QuoteProvider;
import tw.bk.appstocks.model.Quote;
import tw.bk.appstocks.service.StockQuoteService;

@Service
@Order(20)
public class PortfolioAnalysisSkill implements ChatSkill {
    private final PortfolioService portfolioService;
    private final StockQuoteService stockQuoteService;

    @Value("${app.ai.chat.skills.portfolio-analysis.enabled:true}")
    private boolean skillEnabled;

    @Value("${app.ai.chat.skills.portfolio-analysis.timeout-ms:900}")
    private long skillTimeoutMs;

    @Value("${app.ai.chat.portfolio-context.enabled:true}")
    private boolean portfolioContextEnabled;

    @Value("${app.ai.chat.portfolio-context.max-portfolios:3}")
    private int portfolioContextMaxPortfolios;

    public PortfolioAnalysisSkill(PortfolioService portfolioService, StockQuoteService stockQuoteService) {
        this.portfolioService = portfolioService;
        this.stockQuoteService = stockQuoteService;
    }

    @Override
    public ChatSkillSpec spec() {
        return new ChatSkillSpec(
                "portfolio-analysis-skill",
                "1.0.0",
                UserRole.USER,
                skillTimeoutMs,
                skillEnabled,
                "{\"type\":\"object\",\"required\":[\"userId\"]}",
                "{\"type\":\"object\",\"description\":\"portfolio_context block\"}");
    }

    @Override
    public boolean supports(ChatSkillContext context) {
        if (!skillEnabled || !portfolioContextEnabled || context == null) {
            return false;
        }
        return context.userId() != null && context.content() != null && !context.content().isBlank();
    }

    @Override
    public ChatSkillResult execute(ChatSkillContext context) {
        long started = System.currentTimeMillis();
        if (context.userId() == null) {
            return ChatSkillResult.error(spec().name(), ChatSkillErrorCode.SKILL_BAD_INPUT, "userId is required",
                    elapsedMs(started));
        }
        String built = buildPortfolioContext(context.userId());
        if (built == null || built.isBlank()) {
            return ChatSkillResult.miss(spec().name(), elapsedMs(started));
        }
        return ChatSkillResult.hit(spec().name(), built, elapsedMs(started));
    }

    private String buildPortfolioContext(Long userId) {
        int limit = Math.min(Math.max(portfolioContextMaxPortfolios, 1), 10);
        try {
            List<PortfolioChatContext> contexts = portfolioService.listChatContexts(userId, createQuoteProvider());
            if (contexts == null || contexts.isEmpty()) {
                return null;
            }

            StringBuilder sb = new StringBuilder();
            sb.append("portfolio_context:\n");
            int appended = 0;
            for (PortfolioChatContext context : contexts) {
                if (context == null || context.portfolioId() == null) {
                    continue;
                }
                if (appended >= limit) {
                    break;
                }
                appended++;
                sb.append("- portfolio_id: ").append(context.portfolioId()).append('\n');
                if (context.portfolioName() != null && !context.portfolioName().isBlank()) {
                    sb.append("  name: ").append(sanitize(context.portfolioName())).append('\n');
                }
                if (context.baseCurrency() != null && !context.baseCurrency().isBlank()) {
                    sb.append("  base_currency: ").append(context.baseCurrency()).append('\n');
                }
                sb.append("  holdings_count: ").append(context.holdingsCount()).append('\n');
                if (context.totalValue() != null) {
                    sb.append("  total_value: ").append(context.totalValue().toPlainString()).append('\n');
                }
                if (context.cashValue() != null) {
                    sb.append("  cash_value: ").append(context.cashValue().toPlainString()).append('\n');
                }
                if (context.positionsValue() != null) {
                    sb.append("  positions_value: ").append(context.positionsValue().toPlainString()).append('\n');
                }
                if (context.asOfDate() != null) {
                    sb.append("  as_of_date: ").append(context.asOfDate()).append('\n');
                }
                sb.append("  valuation_source: ").append(context.snapshotBacked() ? "snapshot" : "realtime")
                        .append('\n');
            }

            if (appended == 0) {
                return null;
            }
            sb.append("portfolio_context_total_portfolios: ").append(contexts.size()).append('\n');
            if (contexts.size() > appended) {
                sb.append("portfolio_context_truncated: true\n");
            }
            return sb.toString().trim();
        } catch (Exception ex) {
            return null;
        }
    }

    private QuoteProvider createQuoteProvider() {
        return this::resolveCurrentPrice;
    }

    private Optional<BigDecimal> resolveCurrentPrice(String symbolKey) {
        if (symbolKey == null || symbolKey.isBlank()) {
            return Optional.empty();
        }
        try {
            Quote quote = stockQuoteService.getQuote(symbolKey);
            return quote == null ? Optional.empty() : Optional.ofNullable(quote.getPrice());
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    private String sanitize(String value) {
        if (value == null) {
            return "";
        }
        return value.replace('\n', ' ').replace('\r', ' ').trim();
    }

    private long elapsedMs(long started) {
        return Math.max(0L, System.currentTimeMillis() - started);
    }
}

