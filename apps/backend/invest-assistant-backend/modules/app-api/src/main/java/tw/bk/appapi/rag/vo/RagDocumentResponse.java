package tw.bk.appapi.rag.vo;

import lombok.Builder;
import lombok.Data;
import tw.bk.apprag.model.RagDocumentView;

import java.time.Instant;
import java.util.Map;

@Data
@Builder
public class RagDocumentResponse {
    private Long id;
    private String title;
    private String sourceType;
    private String sourceId;
    private Map<String, Object> meta;
    private Instant createdAt;

    public static RagDocumentResponse from(RagDocumentView entity) {
        return RagDocumentResponse.builder()
                .id(entity.id())
                .title(entity.title())
                .sourceType(entity.sourceType())
                .sourceId(entity.sourceId())
                .meta(entity.meta())
                .createdAt(entity.createdAt())
                .build();
    }
}
