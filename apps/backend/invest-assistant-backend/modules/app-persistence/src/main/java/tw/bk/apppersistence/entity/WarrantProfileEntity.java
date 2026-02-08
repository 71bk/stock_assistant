package tw.bk.apppersistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * Warrant profile entity - minimal fields for v1.
 */
@Entity
@Table(name = "warrant_profiles", schema = "app")
@Getter
@Setter
public class WarrantProfileEntity {

    @Id
    @Column(name = "instrument_id")
    private Long instrumentId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "instrument_id", insertable = false, updatable = false)
    private InstrumentEntity instrument;

    @Column(name = "underlying_symbol")
    private String underlyingSymbol;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private OffsetDateTime updatedAt;
}
