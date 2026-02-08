package tw.bk.appapi.rag.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateRagDocumentRequest {
    private String title;
    private String rawText;
    private String fileId;
    private String sourceType;
    private List<String> tags;
}
