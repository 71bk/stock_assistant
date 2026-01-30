package tw.bk.apppersistence.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import tw.bk.apppersistence.entity.AiReportEntity;

public interface AiReportRepository extends JpaRepository<AiReportEntity, Long> {
    Page<AiReportEntity> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Optional<AiReportEntity> findByIdAndUserId(Long id, Long userId);

    List<AiReportEntity> findByUserIdAndReportTypeOrderByCreatedAtDesc(Long userId, String reportType);
}
