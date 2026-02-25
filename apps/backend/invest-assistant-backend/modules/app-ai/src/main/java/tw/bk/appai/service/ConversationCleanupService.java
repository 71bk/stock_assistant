package tw.bk.appai.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationCleanupService {
    private final AiConversationService conversationService;

    @Value("${app.ai.chat.soft-delete.purge.enabled:true}")
    private boolean enabled;

    @Value("${app.ai.chat.soft-delete.purge.batch-size:200}")
    private int batchSize;

    @Scheduled(cron = "${app.ai.chat.soft-delete.purge.cron:0 20 3 * * *}")
    public void purgeExpiredConversations() {
        if (!enabled) {
            return;
        }
        int purged = conversationService.purgeExpiredConversations(batchSize);
        if (purged > 0) {
            log.info("Purged {} expired soft-deleted conversations", purged);
        }
    }
}
