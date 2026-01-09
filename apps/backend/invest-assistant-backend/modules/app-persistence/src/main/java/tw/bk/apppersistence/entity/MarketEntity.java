package tw.bk.apppersistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import tw.bk.appcommon.model.BaseEntity;

@Entity
@Table(name = "markets", schema = "app")
@Getter
@Setter
public class MarketEntity extends BaseEntity {
    @Column(name = "code", nullable = false, unique = true)
    private String code;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "timezone", nullable = false)
    private String timezone;

    @Column(name = "default_currency", nullable = false)
    private String defaultCurrency;
}
