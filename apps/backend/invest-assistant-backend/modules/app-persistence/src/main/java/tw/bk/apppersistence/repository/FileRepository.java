package tw.bk.apppersistence.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import tw.bk.apppersistence.entity.FileEntity;

public interface FileRepository extends JpaRepository<FileEntity, Long> {
    Optional<FileEntity> findByUserIdAndSha256(Long userId, String sha256);

    Optional<FileEntity> findByIdAndUserId(Long id, Long userId);
}
