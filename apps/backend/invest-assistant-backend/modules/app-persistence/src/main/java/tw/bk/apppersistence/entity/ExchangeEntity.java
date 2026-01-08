package tw.bk.apppersistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import tw.bk.appcommon.model.BaseEntity;

@Entity
@Table(name = "exchanges", schema = "app")
public class ExchangeEntity extends BaseEntity {
    @Column(name = "market_id", nullable = false)
    private Long marketId;

    @Column(name = "mic", nullable = false)
    private String mic;

    @Column(name = "code")
    private String code;

    @Column(name = "name", nullable = false)
    private String name;

    public Long getMarketId() {
        return marketId;
    }

    public void setMarketId(Long marketId) {
        this.marketId = marketId;
    }

    public String getMic() {
        return mic;
    }

    public void setMic(String mic) {
        this.mic = mic;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
