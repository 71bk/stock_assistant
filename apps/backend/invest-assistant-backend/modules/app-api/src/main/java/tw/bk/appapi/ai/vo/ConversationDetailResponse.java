package tw.bk.appapi.ai.vo;

import java.time.OffsetDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tw.bk.appai.model.ConversationView;

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

    public static ConversationDetailResponse of(ConversationView entity, List<ConversationMessageResponse> messages) {
        return ConversationDetailResponse.builder()
                .conversationId(entity.id() != null ? entity.id().toString() : null)
                .title(entity.title())
                .summary(entity.summary())
                .createdAt(entity.createdAt())
                .updatedAt(entity.updatedAt())
                .messages(messages)
                .build();
    }
}
