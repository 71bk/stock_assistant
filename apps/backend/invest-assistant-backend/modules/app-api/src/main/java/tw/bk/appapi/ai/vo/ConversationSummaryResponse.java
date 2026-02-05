package tw.bk.appapi.ai.vo;

import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tw.bk.apppersistence.entity.ConversationEntity;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationSummaryResponse {
    private String conversationId;
    private String title;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public static ConversationSummaryResponse from(ConversationEntity entity) {
        return ConversationSummaryResponse.builder()
                .conversationId(entity.getId() != null ? entity.getId().toString() : null)
                .title(entity.getTitle())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
