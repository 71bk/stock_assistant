package tw.bk.apppersistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import tw.bk.appcommon.model.BaseEntity;

@Entity
@Table(name = "statements", schema = "app")
@Getter
@Setter
public class StatementEntity extends BaseEntity {
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "portfolio_id", nullable = false)
    private Long portfolioId;

    @Column(name = "source", nullable = false)
    private String source;

    @Column(name = "file_id")
    private Long fileId;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "period_start")
    private LocalDate periodStart;

    @Column(name = "period_end")
    private LocalDate periodEnd;

    @Column(name = "raw_text")
    private String rawText;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "parsed_json", columnDefinition = "jsonb")
    private String parsedJson;

    @Column(name = "superseded_at")
    private java.time.OffsetDateTime supersededAt;
}
