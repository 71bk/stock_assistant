package tw.bk.appapi.rag.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RagQueryRequest {
    private String query;
    private Integer topK;
    private String sourceType;
}
