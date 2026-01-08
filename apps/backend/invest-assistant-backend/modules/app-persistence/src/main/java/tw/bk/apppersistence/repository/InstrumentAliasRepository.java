package tw.bk.apppersistence.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import tw.bk.apppersistence.entity.InstrumentAliasEntity;

public interface InstrumentAliasRepository extends JpaRepository<InstrumentAliasEntity, Long> {
    List<InstrumentAliasEntity> findByAliasTicker(String aliasTicker);
}
