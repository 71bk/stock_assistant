package tw.bk.appapi.ai.vo;

import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tw.bk.appai.model.ConversationMessageView;
import tw.bk.appcommon.enums.ConversationMessageStatus;
import tw.bk.appcommon.enums.ConversationRole;

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

    public static ConversationMessageResponse from(ConversationMessageView entity) {
        return ConversationMessageResponse.builder()
                .messageId(entity.id() != null ? entity.id().toString() : null)
                .role(entity.role())
                .content(entity.content())
                .status(entity.status())
                .createdAt(entity.createdAt())
                .build();
    }
}
