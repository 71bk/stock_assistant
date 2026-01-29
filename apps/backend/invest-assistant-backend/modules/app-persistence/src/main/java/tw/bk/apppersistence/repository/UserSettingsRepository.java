package tw.bk.apppersistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tw.bk.apppersistence.entity.UserSettingsEntity;

public interface UserSettingsRepository extends JpaRepository<UserSettingsEntity, Long> {
}
