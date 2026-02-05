package tw.bk.appapi.ai.vo;

import java.time.OffsetDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tw.bk.apppersistence.entity.ConversationEntity;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationDetailResponse {
    private String conversationId;
    private String title;
    private String summary;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private List<ConversationMessageResponse> messages;

    public static ConversationDetailResponse of(ConversationEntity entity, List<ConversationMessageResponse> messages) {
        return ConversationDetailResponse.builder()
                .conversationId(entity.getId() != null ? entity.getId().toString() : null)
                .title(entity.getTitle())
                .summary(entity.getSummary())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .messages(messages)
                .build();
    }
}
