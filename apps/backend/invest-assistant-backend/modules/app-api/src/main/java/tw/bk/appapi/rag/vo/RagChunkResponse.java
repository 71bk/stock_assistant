package tw.bk.appapi.rag.vo;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tw.bk.apprag.client.AiWorkerChunk;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RagChunkResponse {
    private String content;
    private String documentId;
    private Integer chunkIndex;
    private Double distance;
    private String title;
    private String sourceType;
    private String sourceId;
    private Map<String, Object> meta;

    public static RagChunkResponse from(AiWorkerChunk chunk) {
        if (chunk == null) {
            return null;
        }
        return RagChunkResponse.builder()
                .content(chunk.getContent())
                .documentId(chunk.getDocumentId())
                .chunkIndex(chunk.getChunkIndex())
                .distance(chunk.getDistance())
                .title(chunk.getTitle())
                .sourceType(chunk.getSourceType())
                .sourceId(chunk.getSourceId())
                .meta(chunk.getMeta())
                .build();
    }
}
