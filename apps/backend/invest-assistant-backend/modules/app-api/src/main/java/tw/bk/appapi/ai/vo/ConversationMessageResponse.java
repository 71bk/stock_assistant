package tw.bk.appapi.ai.vo;

import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tw.bk.appcommon.enums.ConversationMessageStatus;
import tw.bk.appcommon.enums.ConversationRole;
import tw.bk.apppersistence.entity.ConversationMessageEntity;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationMessageResponse {
    private String messageId;
    private ConversationRole role;
    private String content;
    private ConversationMessageStatus status;
    private OffsetDateTime createdAt;

    public static ConversationMessageResponse from(ConversationMessageEntity entity) {
        return ConversationMessageResponse.builder()
                .messageId(entity.getId() != null ? entity.getId().toString() : null)
                .role(entity.getRoleEnum())
                .content(entity.getContent())
                .status(entity.getStatusEnum())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
