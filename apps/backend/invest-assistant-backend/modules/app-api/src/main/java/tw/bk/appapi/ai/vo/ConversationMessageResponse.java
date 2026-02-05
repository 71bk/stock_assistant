package tw.bk.appapi.ai.vo;

import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tw.bk.apppersistence.entity.ConversationMessageEntity;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationMessageResponse {
    private String messageId;
    private String role;
    private String content;
    private String status;
    private OffsetDateTime createdAt;

    public static ConversationMessageResponse from(ConversationMessageEntity entity) {
        return ConversationMessageResponse.builder()
                .messageId(entity.getId() != null ? entity.getId().toString() : null)
                .role(entity.getRole())
                .content(entity.getContent())
                .status(entity.getStatus())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
