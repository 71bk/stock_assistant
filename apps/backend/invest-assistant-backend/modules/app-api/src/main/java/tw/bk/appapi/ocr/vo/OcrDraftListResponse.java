package tw.bk.appapi.ocr.vo;

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
    private List<OcrDraftResponse> items;
}
