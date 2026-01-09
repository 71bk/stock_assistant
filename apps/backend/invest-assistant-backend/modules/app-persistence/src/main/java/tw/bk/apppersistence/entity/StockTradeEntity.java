package tw.bk.apppersistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDate;
import tw.bk.appcommon.model.BaseEntity;

@Entity
@Table(name = "stock_trades", schema = "app")
@Getter
@Setter
public class StockTradeEntity extends BaseEntity {
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "portfolio_id", nullable = false)
    private Long portfolioId;

    @Column(name = "account_id")
    private Long accountId;

    @Column(name = "instrument_id", nullable = false)
    private Long instrumentId;

    @Column(name = "trade_date", nullable = false)
    private LocalDate tradeDate;

    @Column(name = "settlement_date")
    private LocalDate settlementDate;

    @Column(name = "side", nullable = false)
    private String side;

    @Column(name = "quantity", nullable = false)
    private BigDecimal quantity;

    @Column(name = "price", nullable = false)
    private BigDecimal price;

    @Column(name = "currency", nullable = false)
    private String currency;

    @Column(name = "gross_amount")
    private BigDecimal grossAmount;

    @Column(name = "fee")
    private BigDecimal fee;

    @Column(name = "tax")
    private BigDecimal tax;

    @Column(name = "net_amount")
    private BigDecimal netAmount;

    @Column(name = "source", nullable = false)
    private String source;

    @Column(name = "source_ref_id")
    private Long sourceRefId;

    @Column(name = "row_hash", nullable = false, unique = true)
    private String rowHash;
}
