package tw.bk.apppersistence.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import tw.bk.apppersistence.entity.ConversationEntity;

public interface ConversationRepository extends JpaRepository<ConversationEntity, Long> {
    List<ConversationEntity> findByUserIdAndDeletedAtIsNullOrderByUpdatedAtDesc(Long userId);

    Optional<ConversationEntity> findByIdAndUserIdAndDeletedAtIsNull(Long id, Long userId);

    List<ConversationEntity> findByDeletedAtIsNotNullAndPurgeAfterAtLessThanEqualOrderByPurgeAfterAtAsc(
            OffsetDateTime purgeAfterAt,
            Pageable pageable);

    @Modifying
    @Query(value = "UPDATE app.conversations SET updated_at = now() WHERE id = :id AND deleted_at IS NULL",
            nativeQuery = true)
    void touchById(Long id);

    @Modifying
    @Query(value = "UPDATE app.conversations SET title = :title, updated_at = now() "
            + "WHERE id = :id AND deleted_at IS NULL",
            nativeQuery = true)
    void updateTitleById(Long id, String title);
}
