package tw.bk.apppersistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "user_positions", schema = "app")
@IdClass(UserPositionId.class)
@Getter
@Setter
public class UserPositionEntity {
    @Id
    @Column(name = "portfolio_id", nullable = false)
    private Long portfolioId;

    @Id
    @Column(name = "instrument_id", nullable = false)
    private Long instrumentId;

    @Column(name = "total_quantity", nullable = false)
    private BigDecimal totalQuantity;

    @Column(name = "avg_cost_native")
    private BigDecimal avgCostNative;

    @Column(name = "currency", nullable = false)
    private String currency;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}
