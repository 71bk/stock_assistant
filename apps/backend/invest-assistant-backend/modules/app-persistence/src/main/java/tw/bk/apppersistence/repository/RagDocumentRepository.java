package tw.bk.apppersistence.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tw.bk.apppersistence.entity.RagDocumentEntity;

@Repository
public interface RagDocumentRepository extends JpaRepository<RagDocumentEntity, Long> {
    Page<RagDocumentEntity> findByUserId(Long userId, Pageable pageable);
}
