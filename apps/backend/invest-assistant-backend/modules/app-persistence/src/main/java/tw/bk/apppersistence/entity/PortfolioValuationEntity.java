package tw.bk.apppersistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "portfolio_valuations", schema = "app")
@IdClass(PortfolioValuationId.class)
@Getter
@Setter
public class PortfolioValuationEntity {
    @Id
    @Column(name = "portfolio_id", nullable = false)
    private Long portfolioId;

    @Id
    @Column(name = "as_of_date", nullable = false)
    private LocalDate asOfDate;

    @Column(name = "base_currency", nullable = false)
    private String baseCurrency;

    @Column(name = "total_value", nullable = false)
    private BigDecimal totalValue;

    @Column(name = "cash_value", nullable = false)
    private BigDecimal cashValue;

    @Column(name = "positions_value", nullable = false)
    private BigDecimal positionsValue;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;
}
