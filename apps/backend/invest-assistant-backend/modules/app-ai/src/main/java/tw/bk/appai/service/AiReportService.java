package tw.bk.appai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tw.bk.appai.model.AiAnalysisInput;
import tw.bk.appai.model.AiReportContext;
import tw.bk.appcommon.error.ErrorCode;
import tw.bk.appcommon.exception.BusinessException;
import tw.bk.apppersistence.entity.AiReportEntity;
import tw.bk.apppersistence.entity.InstrumentEntity;
import tw.bk.apppersistence.entity.PortfolioEntity;
import tw.bk.apppersistence.repository.AiReportRepository;
import tw.bk.apppersistence.repository.InstrumentRepository;
import tw.bk.apppersistence.repository.PortfolioRepository;
import tw.bk.apppersistence.repository.StockTradeRepository;
import tw.bk.apppersistence.repository.UserPositionRepository;

/**
 * AI 報告服務。
 * 負責準備分析上下文、儲存報告、查詢歷史報告。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AiReportService {
    private static final String TYPE_INSTRUMENT = "INSTRUMENT";
    private static final String TYPE_PORTFOLIO = "PORTFOLIO";
    private static final String TYPE_GENERAL = "GENERAL";

    private final AiReportRepository aiReportRepository;
    private final PortfolioRepository portfolioRepository;
    private final UserPositionRepository userPositionRepository;
    private final StockTradeRepository stockTradeRepository;
    private final InstrumentRepository instrumentRepository;
    private final ObjectMapper objectMapper;

    /**
     * 列出使用者的 AI 報告（分頁）。
     */
    @Transactional(readOnly = true)
    public Page<AiReportEntity> listReports(Long userId, Pageable pageable) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED, "Unauthorized");
        }
        return aiReportRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
    }

    /**
     * 取得單一 AI 報告詳情。
     */
    @Transactional(readOnly = true)
    public AiReportEntity getReport(Long userId, Long reportId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED, "Unauthorized");
        }
        return aiReportRepository.findByIdAndUserId(reportId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Report not found"));
    }

    /**
     * 準備 AI 分析上下文。
     * 驗證輸入、解析 reportType、查詢相關資料（portfolio/instrument）。
     * 
     * @param input 分析輸入
     * @return 準備好的上下文，可用於建立 LLM prompt
     */
    @Transactional(readOnly = true)
    public AiReportContext prepareContext(AiAnalysisInput input) {
        if (input == null || input.getUserId() == null) {
            throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED, "Unauthorized");
        }

        ResolvedInput resolved = resolveInput(input);
        Map<String, Object> summary = buildInputSummary(
                input, resolved.reportType, resolved.portfolio, resolved.portfolioId, resolved.instrumentId);

        String portfolioName = resolved.portfolio != null ? resolved.portfolio.getName() : null;
        String portfolioCurrency = resolved.portfolio != null ? resolved.portfolio.getBaseCurrency() : null;
        String instrumentDisplay = resolved.instrument != null
                ? resolved.instrument.getTicker() + " "
                        + firstNonBlank(resolved.instrument.getNameZh(), resolved.instrument.getNameEn())
                : null;

        log.debug("Prepared AI context: reportType={}, portfolioId={}, instrumentId={}",
                resolved.reportType, resolved.portfolioId, resolved.instrumentId);

        return AiReportContext.builder()
                .userId(input.getUserId())
                .portfolioId(resolved.portfolioId)
                .instrumentId(resolved.instrumentId)
                .reportType(resolved.reportType)
                .prompt(input.getPrompt())
                .summary(summary)
                .portfolioName(portfolioName)
                .portfolioCurrency(portfolioCurrency)
                .instrumentDisplay(instrumentDisplay)
                .build();
    }

    /**
     * 儲存 AI 報告。
     * 注意：此方法會開啟新的交易，確保從 async 呼叫時也能正確儲存。
     *
     * @param context    分析上下文
     * @param outputText LLM 回覆的完整文字
     * @return 儲存後的報告實體
     */
    @Transactional
    public AiReportEntity saveReport(AiReportContext context, String outputText) {
        if (context == null || context.getUserId() == null) {
            throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED, "Unauthorized");
        }

        AiReportEntity entity = new AiReportEntity();
        entity.setUserId(context.getUserId());
        entity.setInstrumentId(context.getInstrumentId());
        entity.setPortfolioId(context.getPortfolioId());
        entity.setReportType(context.getReportType());
        entity.setInputSummary(toJson(context.getSummary()));
        entity.setOutputText(outputText == null ? "" : outputText);

        AiReportEntity saved = aiReportRepository.save(entity);
        log.info("Saved AI report: id={}, userId={}, reportType={}",
                saved.getId(), saved.getUserId(), saved.getReportType());
        return saved;
    }

    /**
     * 產生報告（非串流版本，用於測試或備用）。
     */
    @Transactional
    public AiReportEntity generateReport(AiAnalysisInput input) {
        if (input == null || input.getUserId() == null) {
            throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED, "Unauthorized");
        }

        AiReportContext context = prepareContext(input);
        String outputText = buildOutputText(context);
        return saveReport(context, outputText);
    }

    // ============================================================
    // Private Helper Methods
    // ============================================================

    /**
     * 正規化 reportType，若未指定則根據 portfolioId/instrumentId 推斷。
     */
    private String normalizeReportType(AiAnalysisInput input) {
        if (input.getReportType() != null && !input.getReportType().isBlank()) {
            return input.getReportType().trim().toUpperCase();
        }
        if (input.getPortfolioId() != null) {
            return TYPE_PORTFOLIO;
        }
        if (input.getInstrumentId() != null) {
            return TYPE_INSTRUMENT;
        }
        return TYPE_GENERAL;
    }

    /**
     * 驗證 reportType 是否為有效值。
     */
    private void validateReportType(String reportType) {
        if (!TYPE_INSTRUMENT.equals(reportType)
                && !TYPE_PORTFOLIO.equals(reportType)
                && !TYPE_GENERAL.equals(reportType)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Invalid report_type");
        }
    }

    /**
     * 建立 inputSummary，包含報告相關的統計資料。
     */
    private Map<String, Object> buildInputSummary(AiAnalysisInput input,
            String reportType,
            PortfolioEntity portfolio,
            Long portfolioId,
            Long instrumentId) {
        Map<String, Object> summary = new HashMap<>();
        summary.put("reportType", reportType);
        summary.put("portfolioId", portfolioId);
        summary.put("instrumentId", instrumentId);
        summary.put("prompt", input.getPrompt());

        // 若為 PORTFOLIO 類型，額外查詢持倉和交易數量
        if (TYPE_PORTFOLIO.equals(reportType) && portfolio != null) {
            long positions = userPositionRepository.countByPortfolioId(portfolio.getId());
            long tradeCount = stockTradeRepository
                    .findByUserIdAndPortfolioId(input.getUserId(), portfolio.getId(), PageRequest.of(0, 1))
                    .getTotalElements();
            summary.put("positions", positions);
            summary.put("tradeCount", tradeCount);
        }
        return summary;
    }

    /**
     * 建立非串流版本的輸出文字（備用）。
     */
    private String buildOutputText(AiReportContext context) {
        if (context == null) {
            return "";
        }
        String reportType = context.getReportType();
        Map<String, Object> summary = context.getSummary() == null ? Map.of() : context.getSummary();

        if (TYPE_PORTFOLIO.equals(reportType)) {
            Object positions = summary.getOrDefault("positions", 0);
            Object tradeCount = summary.getOrDefault("tradeCount", 0);
            String name = context.getPortfolioName() == null ? "" : context.getPortfolioName();
            String currency = context.getPortfolioCurrency() == null ? "" : context.getPortfolioCurrency();
            return "Portfolio \"" + name + "\" has " + positions
                    + " positions and " + tradeCount + " trades. Base currency: "
                    + currency + ".";
        }

        if (TYPE_INSTRUMENT.equals(reportType)) {
            String display = context.getInstrumentDisplay() == null ? "" : context.getInstrumentDisplay();
            return "Instrument \"" + display + "\" analysis is not enabled yet.";
        }

        String prompt = context.getPrompt();
        if (prompt == null || prompt.isBlank()) {
            return "No analysis prompt provided.";
        }
        return "Analysis request: " + prompt.trim();
    }

    /**
     * 將 Map 轉為 JSON 字串。
     */
    private String toJson(Map<String, Object> summary) {
        try {
            Map<String, Object> safeSummary = summary == null ? Map.of() : summary;
            return objectMapper.writeValueAsString(safeSummary);
        } catch (Exception ex) {
            log.warn("Failed to serialize summary: {}", ex.getMessage());
            return "{}";
        }
    }

    /**
     * 取第一個非空白字串。
     */
    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        if (second != null && !second.isBlank()) {
            return second;
        }
        return "";
    }

    /**
     * 解析並驗證輸入，回傳正規化後的資料。
     */
    private ResolvedInput resolveInput(AiAnalysisInput input) {
        String reportType = normalizeReportType(input);
        validateReportType(reportType);

        Long portfolioId = input.getPortfolioId();
        Long instrumentId = input.getInstrumentId();

        // 根據 reportType 清除不相關的 ID
        if (TYPE_GENERAL.equals(reportType)) {
            portfolioId = null;
            instrumentId = null;
        } else if (TYPE_PORTFOLIO.equals(reportType)) {
            instrumentId = null;
        } else if (TYPE_INSTRUMENT.equals(reportType)) {
            portfolioId = null;
        }

        PortfolioEntity portfolio = null;
        InstrumentEntity instrument = null;

        // PORTFOLIO 類型需查詢並驗證 portfolio
        if (TYPE_PORTFOLIO.equals(reportType)) {
            if (portfolioId == null) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "portfolio_id is required");
            }
            portfolio = portfolioRepository.findByIdAndUserId(portfolioId, input.getUserId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Portfolio not found"));
        }

        // INSTRUMENT 類型需查詢 instrument
        if (TYPE_INSTRUMENT.equals(reportType)) {
            if (instrumentId == null) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "instrument_id is required");
            }
            instrument = instrumentRepository.findById(instrumentId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Instrument not found"));
        }

        return new ResolvedInput(reportType, portfolioId, instrumentId, portfolio, instrument);
    }

    /**
     * 內部類別：解析後的輸入資料。
     */
    private static final class ResolvedInput {
        private final String reportType;
        private final Long portfolioId;
        private final Long instrumentId;
        private final PortfolioEntity portfolio;
        private final InstrumentEntity instrument;

        private ResolvedInput(String reportType,
                Long portfolioId,
                Long instrumentId,
                PortfolioEntity portfolio,
                InstrumentEntity instrument) {
            this.reportType = reportType;
            this.portfolioId = portfolioId;
            this.instrumentId = instrumentId;
            this.portfolio = portfolio;
            this.instrument = instrument;
        }
    }
}
