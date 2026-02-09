package tw.bk.appai.model;

import java.time.OffsetDateTime;
import tw.bk.appcommon.enums.ConversationMessageStatus;
import tw.bk.appcommon.enums.ConversationRole;

public record ConversationMessageView(
        Long id,
        ConversationRole role,
        String content,
        ConversationMessageStatus status,
        OffsetDateTime createdAt) {
}
