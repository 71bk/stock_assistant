package tw.bk.appai.model;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI 報告生成的上下文資訊。
 * 用於在 prepareContext -> streamChat -> saveReport 流程中傳遞資料。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiReportContext {
    /** 使用者 ID */
    private Long userId;

    /** 投資組合 ID（若為 PORTFOLIO 類型） */
    private Long portfolioId;

    /** 標的 ID（若為 INSTRUMENT 類型） */
    private Long instrumentId;

    /** 報告類型：PORTFOLIO / INSTRUMENT / GENERAL */
    private String reportType;

    /** 使用者輸入的 prompt */
    private String prompt;

    /** 彙整資料（用於 inputSummary 存儲） */
    private Map<String, Object> summary;

    /** 投資組合名稱（用於 LLM prompt） */
    private String portfolioName;

    /** 投資組合基礎幣別 */
    private String portfolioCurrency;

    /** 標的顯示名稱（如 "2330 台積電"） */
    private String instrumentDisplay;
}
