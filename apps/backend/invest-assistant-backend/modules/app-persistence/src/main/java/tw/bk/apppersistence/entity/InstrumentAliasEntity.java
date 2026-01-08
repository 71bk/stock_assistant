package tw.bk.apppersistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import tw.bk.appcommon.model.BaseEntity;

@Entity
@Table(name = "instrument_aliases", schema = "app")
public class InstrumentAliasEntity extends BaseEntity {
    @Column(name = "instrument_id", nullable = false)
    private Long instrumentId;

    @Column(name = "source", nullable = false)
    private String source;

    @Column(name = "alias_ticker", nullable = false)
    private String aliasTicker;

    public Long getInstrumentId() {
        return instrumentId;
    }

    public void setInstrumentId(Long instrumentId) {
        this.instrumentId = instrumentId;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getAliasTicker() {
        return aliasTicker;
    }

    public void setAliasTicker(String aliasTicker) {
        this.aliasTicker = aliasTicker;
    }
}
