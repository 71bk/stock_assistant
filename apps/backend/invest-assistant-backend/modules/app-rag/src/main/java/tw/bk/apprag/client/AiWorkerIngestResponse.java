package tw.bk.apprag.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class AiWorkerIngestResponse {
    @JsonProperty("document_id")
    private String documentId;

    private String title;

    @JsonProperty("chunks_count")
    private Integer chunksCount;

    private String status;

    private String message;
}
