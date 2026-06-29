package tw.bk.appapi.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import tw.bk.appai.model.AiReportContext;
import tw.bk.appcommon.enums.AiReportType;

/**
 * 組裝 AI 分析所需的 LLM 訊息（system + user）。
 *
 * <p>把硬編碼的 system prompt、預設 prompt 與 summary 序列化從 {@code AiController}
 * 抽出，讓 controller / use case 不必知道 prompt 細節。
 */
class AnalysisPromptProvider {
    private static final String SYSTEM_PROMPT = """
            You are a financial analysis assistant. Be concise and factual.
            You are replying in a web UI that supports Markdown rendering.
            Detect the user's primary language from their latest message and reply in that language.
            If the user writes in Traditional Chinese, reply in Traditional Chinese.
            Do not reply in Simplified Chinese unless the user uses Simplified Chinese.
            Use standard Markdown formatting for the response.
            For lists, use valid Markdown list syntax such as `- ` or `1. `.
            Separate paragraphs, tables, and lists with a blank line.
            When presenting tabular data, use valid GitHub Flavored Markdown tables.
            Tables must include a header row and a separator row, for example:
            | 欄位一 | 欄位二 |
            |---|---|
            | 值一 | 值二 |
            請務必使用標準的 Markdown Table 格式來呈現表格數據，不要使用空白鍵對齊。
            Every table row must have the same number of columns as the header.
            If a cell value contains a literal pipe character, rewrite or escape it so the table remains valid.
            """;

    private final ObjectMapper objectMapper;

    AnalysisPromptProvider(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** 依分析上下文建立 LLM 訊息列表（system + user）。 */
    List<Map<String, String>> buildMessages(AiReportContext context) {
        String prompt = resolvePrompt(context);
        String summaryJson = toJson(context.getSummary());

        StringBuilder userContent = new StringBuilder();
        if (context.getReportType() != null) {
            userContent.append("report_type: ").append(context.getReportType());
        }
        if (context.getInstrumentDisplay() != null && !context.getInstrumentDisplay().isBlank()) {
            userContent.append("\n").append("instrument: ").append(context.getInstrumentDisplay());
        }
        if (context.getPortfolioName() != null && !context.getPortfolioName().isBlank()) {
            userContent.append("\n").append("portfolio: ").append(context.getPortfolioName());
        }
        if (context.getPortfolioCurrency() != null && !context.getPortfolioCurrency().isBlank()) {
            userContent.append("\n").append("base_currency: ").append(context.getPortfolioCurrency());
        }
        if (summaryJson != null && !summaryJson.isBlank()) {
            userContent.append("\n").append("summary: ").append(summaryJson);
        }
        userContent.append("\n").append("request: ").append(prompt);

        return List.of(
                Map.of("role", "system", "content", SYSTEM_PROMPT),
                Map.of("role", "user", "content", userContent.toString()));
    }

    private String resolvePrompt(AiReportContext context) {
        String prompt = context.getPrompt();
        if (prompt != null && !prompt.isBlank()) {
            return prompt.trim();
        }
        AiReportType reportType = AiReportType.from(context.getReportType());
        if (AiReportType.PORTFOLIO.equals(reportType)) {
            return "Summarize the portfolio and highlight key risks.";
        }
        if (AiReportType.INSTRUMENT.equals(reportType)) {
            return "Summarize the instrument and highlight key risks.";
        }
        return "Provide a concise market summary.";
    }

    private String toJson(Map<String, Object> value) {
        try {
            Map<String, Object> safe = value == null ? Map.of() : value;
            return objectMapper.writeValueAsString(safe);
        } catch (Exception ex) {
            return "{}";
        }
    }
}
