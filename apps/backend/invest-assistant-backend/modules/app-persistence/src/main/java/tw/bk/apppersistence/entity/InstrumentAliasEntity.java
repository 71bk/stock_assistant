package tw.bk.apppersistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import tw.bk.appcommon.model.BaseEntity;

@Entity
@Table(name = "instrument_aliases", schema = "app")
@Getter
@Setter
public class InstrumentAliasEntity extends BaseEntity {
    @Column(name = "instrument_id", nullable = false)
    private Long instrumentId;

    @Column(name = "source", nullable = false)
    private String source;

    @Column(name = "alias_ticker", nullable = false)
    private String aliasTicker;
}
