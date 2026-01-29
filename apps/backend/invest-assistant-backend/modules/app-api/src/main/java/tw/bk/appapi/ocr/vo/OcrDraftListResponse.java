package tw.bk.appapi.ocr.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OcrDraftListResponse {
    @JsonProperty("items")
    private List<OcrDraftResponse> items;
}
