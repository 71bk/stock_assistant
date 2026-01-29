package tw.bk.appapi.ocr.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateOcrJobRequest {
    @NotBlank(message = "file_id is required")
    @JsonProperty("file_id")
    private String fileId;

    @NotBlank(message = "portfolio_id is required")
    @JsonProperty("portfolio_id")
    private String portfolioId;
}
