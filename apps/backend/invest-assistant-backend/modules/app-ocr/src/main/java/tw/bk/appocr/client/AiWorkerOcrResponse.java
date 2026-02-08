package tw.bk.appocr.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;

public record AiWorkerOcrResponse(
                @JsonProperty("raw_text") String rawText,
                @JsonProperty("trades") List<AiWorkerParsedTrade> trades,
                @JsonProperty("confidence") BigDecimal confidence,
                @JsonProperty("warnings") List<String> warnings,
                @JsonProperty("broker_detected") String brokerDetected,
                @JsonProperty("original_date_format") String originalDateFormat,
                @JsonProperty("ocr_method") String ocrMethod) {
}
