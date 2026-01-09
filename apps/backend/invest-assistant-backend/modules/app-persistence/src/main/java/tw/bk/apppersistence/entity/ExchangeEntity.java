package tw.bk.apppersistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import tw.bk.appcommon.model.BaseEntity;

@Entity
@Table(name = "exchanges", schema = "app")
@Getter
@Setter
public class ExchangeEntity extends BaseEntity {
    @Column(name = "market_id", nullable = false)
    private Long marketId;

    @Column(name = "mic", nullable = false)
    private String mic;

    @Column(name = "code")
    private String code;

    @Column(name = "name", nullable = false)
    private String name;
}
