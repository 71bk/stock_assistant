package tw.bk.apppersistence.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import tw.bk.apppersistence.entity.ConversationMessageEntity;

public interface ConversationMessageRepository extends JpaRepository<ConversationMessageEntity, Long> {
    Optional<ConversationMessageEntity> findByConversationIdAndClientMessageId(Long conversationId,
            String clientMessageId);

    List<ConversationMessageEntity> findByConversationIdOrderByIdAsc(Long conversationId);

    List<ConversationMessageEntity> findByConversationIdOrderByIdDesc(Long conversationId, Pageable pageable);

    List<ConversationMessageEntity> findByConversationIdAndRoleNotOrderByIdDesc(Long conversationId, String role,
            Pageable pageable);

    Optional<ConversationMessageEntity> findFirstByConversationIdAndRoleOrderByIdAsc(Long conversationId, String role);
}
