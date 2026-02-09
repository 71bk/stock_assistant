package tw.bk.apprag.model;

import java.time.Instant;
import java.util.Map;

public record RagDocumentView(
        Long id,
        Long userId,
        String title,
        String sourceType,
        String sourceId,
        Map<String, Object> meta,
        Instant createdAt) {
}
