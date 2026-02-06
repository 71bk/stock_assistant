package tw.bk.apppersistence.repository;

import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import tw.bk.apppersistence.entity.StatementEntity;

public interface StatementRepository extends JpaRepository<StatementEntity, Long> {
    Optional<StatementEntity> findByIdAndUserId(Long id, Long userId);

    Page<StatementEntity> findByStatusAndSupersededAtBeforeOrderBySupersededAtAsc(
            String status, OffsetDateTime cutoff, Pageable pageable);
}
