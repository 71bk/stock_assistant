package tw.bk.apppersistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "ai_reports", schema = "app")
@Getter
@Setter
public class AiReportEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "instrument_id")
    private Long instrumentId;

    @Column(name = "portfolio_id")
    private Long portfolioId;

    @Column(name = "report_type", nullable = false)
    private String reportType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "input_summary", nullable = false, columnDefinition = "jsonb")
    private String inputSummary;

    @Column(name = "output_text", nullable = false)
    private String outputText;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;
}
