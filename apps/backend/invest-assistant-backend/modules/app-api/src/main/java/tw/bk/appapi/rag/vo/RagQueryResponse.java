package tw.bk.appapi.rag.vo;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tw.bk.apprag.client.AiWorkerQueryResponse;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RagQueryResponse {
    private List<RagChunkResponse> chunks;

    public static RagQueryResponse from(AiWorkerQueryResponse response) {
        if (response == null || response.getChunks() == null) {
            return RagQueryResponse.builder().chunks(List.of()).build();
        }
        List<RagChunkResponse> items = response.getChunks().stream()
                .map(RagChunkResponse::from)
                .toList();
        return RagQueryResponse.builder().chunks(items).build();
    }
}
