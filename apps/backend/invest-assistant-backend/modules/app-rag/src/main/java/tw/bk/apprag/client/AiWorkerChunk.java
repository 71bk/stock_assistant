package tw.bk.apprag.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;
import lombok.Data;

@Data
public class AiWorkerChunk {
    private String content;

    @JsonProperty("document_id")
    private String documentId;

    @JsonProperty("chunk_index")
    private Integer chunkIndex;

    private Double distance;

    private String title;

    @JsonProperty("source_type")
    private String sourceType;

    @JsonProperty("source_id")
    private String sourceId;

    private Map<String, Object> meta;
}
