package tw.bk.appai.model;

import java.time.OffsetDateTime;

public record ConversationView(
        Long id,
        String title,
        String promptVersion,
        String promptSnapshot,
        String summary,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {
}
