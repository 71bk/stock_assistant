package tw.bk.apppersistence.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import tw.bk.apppersistence.entity.OcrJobEntity;

public interface OcrJobRepository extends JpaRepository<OcrJobEntity, Long> {
    Optional<OcrJobEntity> findByIdAndUserId(Long id, Long userId);

    Optional<OcrJobEntity> findByStatementId(Long statementId);
}
