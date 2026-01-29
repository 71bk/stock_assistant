package tw.bk.apppersistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import tw.bk.appcommon.model.BaseEntity;

@Entity
@Table(name = "instruments", schema = "app")
@Getter
@Setter
public class InstrumentEntity extends BaseEntity {

    @Column(name = "market_id", nullable = false, insertable = false, updatable = false)
    private Long marketId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "market_id")
    private MarketEntity market;

    @Column(name = "exchange_id", nullable = false, insertable = false, updatable = false)
    private Long exchangeId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exchange_id")
    private ExchangeEntity exchange;

    @Column(name = "ticker", nullable = false)
    private String ticker;

    @Column(name = "name_zh")
    private String nameZh;

    @Column(name = "name_en")
    private String nameEn;

    @Column(name = "currency", nullable = false)
    private String currency;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "symbol_key", nullable = false, unique = true)
    private String symbolKey;

    @Column(name = "asset_type", nullable = false)
    private String assetType = "STOCK";
}
