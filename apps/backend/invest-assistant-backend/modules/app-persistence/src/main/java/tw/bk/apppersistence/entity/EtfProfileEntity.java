package tw.bk.apppersistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * ETF Profile Entity - stores ETF-specific information
 * One-to-One relationship with InstrumentEntity
 */
@Entity
@Table(name = "etf_profiles", schema = "app")
@Getter
@Setter
public class EtfProfileEntity {

    @Id
    @Column(name = "instrument_id")
    private Long instrumentId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "instrument_id")
    private InstrumentEntity instrument;

    @Column(name = "underlying_type", nullable = false)
    private String underlyingType;

    @Column(name = "underlying_name", nullable = false)
    private String underlyingName;

    @Column(name = "as_of_date")
    private java.time.LocalDate asOfDate;

    @Column(name = "updated_at", nullable = false)
    private java.time.Instant updatedAt;
}
