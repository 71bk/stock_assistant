package tw.bk.apppersistence.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import tw.bk.apppersistence.entity.StatementTradeEntity;

public interface StatementTradeRepository extends JpaRepository<StatementTradeEntity, Long> {
    List<StatementTradeEntity> findByStatementIdOrderByIdAsc(Long statementId);

    Optional<StatementTradeEntity> findByIdAndStatementId(Long id, Long statementId);
}
