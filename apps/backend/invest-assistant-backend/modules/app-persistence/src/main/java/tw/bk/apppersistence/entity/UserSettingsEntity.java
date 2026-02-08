package tw.bk.apppersistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "user_settings", schema = "app")
@Getter
@Setter
public class UserSettingsEntity {
    @Id
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "base_currency", nullable = false)
    private String baseCurrency;

    @Column(name = "display_timezone", nullable = false)
    private String displayTimezone;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private OffsetDateTime updatedAt;
}
