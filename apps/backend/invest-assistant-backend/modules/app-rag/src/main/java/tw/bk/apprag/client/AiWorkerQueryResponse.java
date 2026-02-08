package tw.bk.apprag.client;

import java.util.List;
import lombok.Data;

@Data
public class AiWorkerQueryResponse {
    private List<AiWorkerChunk> chunks;
}
