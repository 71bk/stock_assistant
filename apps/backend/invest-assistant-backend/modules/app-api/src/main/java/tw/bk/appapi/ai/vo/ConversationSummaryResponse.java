package tw.bk.appapi.ai.vo;

import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tw.bk.appai.model.ConversationView;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationSummaryResponse {
    private String conversationId;
    private String title;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public static ConversationSummaryResponse from(ConversationView entity) {
        return ConversationSummaryResponse.builder()
                .conversationId(entity.id() != null ? entity.id().toString() : null)
                .title(entity.title())
                .createdAt(entity.createdAt())
                .updatedAt(entity.updatedAt())
                .build();
    }
}
