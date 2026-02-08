package tw.bk.appapi.ocr.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 建立 OCR Job 請求。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateOcrJobRequest {
    /** 檔案 ID（必填） */
    @NotBlank(message = "fileId is required")
    private String fileId;

    /** 投資組合 ID（必填） */
    @NotBlank(message = "portfolioId is required")
    private String portfolioId;

    /** 是否強制重新處理（忽略去重邏輯） */
    private Boolean force;
}
