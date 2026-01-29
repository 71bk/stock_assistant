package tw.bk.apppersistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "statement_trades", schema = "app")
@Getter
@Setter
public class StatementTradeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "statement_id", nullable = false)
    private Long statementId;

    @Column(name = "instrument_id")
    private Long instrumentId;

    @Column(name = "raw_ticker")
    private String rawTicker;

    @Column(name = "name")
    private String name;

    @Column(name = "trade_date")
    private LocalDate tradeDate;

    @Column(name = "settlement_date")
    private LocalDate settlementDate;

    @Column(name = "side")
    private String side;

    @Column(name = "quantity")
    private BigDecimal quantity;

    @Column(name = "price")
    private BigDecimal price;

    @Column(name = "currency")
    private String currency;

    @Column(name = "fee")
    private BigDecimal fee;

    @Column(name = "tax")
    private BigDecimal tax;

    @Column(name = "net_amount")
    private BigDecimal netAmount;

    @Column(name = "row_hash", nullable = false)
    private String rowHash;

    @Column(name = "errors_json", nullable = false, columnDefinition = "jsonb")
    private String errorsJson;

    @Column(name = "warnings_json", nullable = false, columnDefinition = "jsonb")
    private String warningsJson;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;
}
