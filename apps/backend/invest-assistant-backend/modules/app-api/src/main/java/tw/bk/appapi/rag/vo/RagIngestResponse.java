package tw.bk.appapi.rag.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tw.bk.apprag.client.AiWorkerIngestResponse;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RagIngestResponse {
    private String documentId;
    private String title;
    private Integer chunksCount;
    private String status;
    private String message;

    public static RagIngestResponse from(AiWorkerIngestResponse response) {
        if (response == null) {
            return null;
        }
        return RagIngestResponse.builder()
                .documentId(response.getDocumentId())
                .title(response.getTitle())
                .chunksCount(response.getChunksCount())
                .status(response.getStatus())
                .message(response.getMessage())
                .build();
    }
}
