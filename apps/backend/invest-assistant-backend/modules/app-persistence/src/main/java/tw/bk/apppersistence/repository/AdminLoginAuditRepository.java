package tw.bk.apppersistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tw.bk.apppersistence.entity.AdminLoginAuditEntity;

public interface AdminLoginAuditRepository extends JpaRepository<AdminLoginAuditEntity, Long> {
}

