package tw.bk.apppersistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import tw.bk.appcommon.model.BaseEntity;

import java.time.LocalDateTime;

@Entity
@Table(name = "users", schema = "app")
@Getter
@Setter
public class UserEntity extends BaseEntity {
    @Column(name = "email", nullable = false, unique = true)
    private String email;

    @Column(name = "google_sub", nullable = false, unique = true)
    private String googleSub;

    @Column(name = "display_name")
    private String displayName;

    @Column(name = "picture_url")
    private String pictureUrl;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;
}
