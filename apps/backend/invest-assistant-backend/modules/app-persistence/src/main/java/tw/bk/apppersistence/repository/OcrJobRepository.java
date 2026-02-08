package tw.bk.apppersistence.repository;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import tw.bk.apppersistence.entity.OcrJobEntity;

public interface OcrJobRepository extends JpaRepository<OcrJobEntity, Long> {
    Optional<OcrJobEntity> findByIdAndUserId(Long id, Long userId);

    Optional<OcrJobEntity> findByStatementId(Long statementId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select j from OcrJobEntity j where j.id = :id and j.userId = :userId")
    Optional<OcrJobEntity> findByIdAndUserIdForUpdate(@Param("id") Long id, @Param("userId") Long userId);

    @Transactional
    @Modifying
    @Query("""
            UPDATE OcrJobEntity j
            SET j.status = :status,
                j.progress = :progress,
                j.errorMessage = :errorMessage
            WHERE j.id = :id
              AND j.status <> :cancelled
            """)
    int updateStatusIfNotCancelled(@Param("id") Long id,
                                   @Param("status") String status,
                                   @Param("progress") Integer progress,
                                   @Param("errorMessage") String errorMessage,
                                   @Param("cancelled") String cancelled);

    @Transactional
    @Modifying
    @Query("""
            UPDATE OcrJobEntity j
            SET j.status = :status,
                j.progress = :progress,
                j.errorMessage = :errorMessage
            WHERE j.id = :id
              AND j.status <> :cancelled
              AND (
                  j.status IN :claimable
                  OR (j.status = :running AND (j.updatedAt IS NULL OR j.updatedAt < :staleBefore))
              )
            """)
    int claimForRunning(@Param("id") Long id,
                        @Param("status") String status,
                        @Param("progress") Integer progress,
                        @Param("errorMessage") String errorMessage,
                        @Param("cancelled") String cancelled,
                        @Param("running") String running,
                        @Param("staleBefore") OffsetDateTime staleBefore,
                        @Param("claimable") Collection<String> claimable);
}
