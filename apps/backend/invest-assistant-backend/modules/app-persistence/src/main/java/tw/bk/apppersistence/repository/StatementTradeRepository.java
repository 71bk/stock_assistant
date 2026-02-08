package tw.bk.apppersistence.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import tw.bk.apppersistence.entity.StatementTradeEntity;

public interface StatementTradeRepository extends JpaRepository<StatementTradeEntity, Long> {
    List<StatementTradeEntity> findByStatementIdOrderByIdAsc(Long statementId);

    Optional<StatementTradeEntity> findByIdAndStatementId(Long id, Long statementId);

    @Query("SELECT e.rowHash FROM StatementTradeEntity e WHERE e.statementId = :statementId")
    List<String> findRowHashesByStatementId(@Param("statementId") Long statementId);

    /**
     * 刪除指定 statement 的所有草稿交易。
     * 用於確認匯入後清理草稿資料。
     */
    @Modifying
    @Query("DELETE FROM StatementTradeEntity e WHERE e.statementId = :statementId")
    void deleteByStatementId(Long statementId);
}
