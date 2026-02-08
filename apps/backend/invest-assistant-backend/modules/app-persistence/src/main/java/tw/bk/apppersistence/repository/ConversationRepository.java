package tw.bk.apppersistence.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import tw.bk.apppersistence.entity.ConversationEntity;

public interface ConversationRepository extends JpaRepository<ConversationEntity, Long> {
    List<ConversationEntity> findByUserIdOrderByUpdatedAtDesc(Long userId);

    Optional<ConversationEntity> findByIdAndUserId(Long id, Long userId);

    @Modifying
    @Query(value = "UPDATE app.conversations SET updated_at = now() WHERE id = :id", nativeQuery = true)
    void touchById(Long id);

    @Modifying
    @Query(value = "UPDATE app.conversations SET title = :title, updated_at = now() WHERE id = :id",
            nativeQuery = true)
    void updateTitleById(Long id, String title);
}
