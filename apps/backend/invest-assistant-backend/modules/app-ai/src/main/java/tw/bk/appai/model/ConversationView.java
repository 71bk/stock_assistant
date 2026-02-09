package tw.bk.appai.model;

import java.time.OffsetDateTime;

public record ConversationView(
        Long id,
        String title,
        String summary,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {
}
