package tw.bk.appapi.rag.vo;

import lombok.Builder;
import lombok.Data;
import tw.bk.apppersistence.entity.RagDocumentEntity;

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

    public static RagDocumentResponse from(RagDocumentEntity entity) {
        return RagDocumentResponse.builder()
                .id(entity.getId())
                .title(entity.getTitle())
                .sourceType(entity.getSourceType())
                .sourceId(entity.getSourceId())
                .meta(entity.getMeta())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
