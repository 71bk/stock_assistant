package tw.bk.apppersistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import tw.bk.appcommon.model.BaseEntity;

@Entity
@Table(name = "ocr_jobs", schema = "app")
@Getter
@Setter
public class OcrJobEntity extends BaseEntity {
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "file_id", nullable = false)
    private Long fileId;

    @Column(name = "statement_id")
    private Long statementId;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "progress", nullable = false)
    private Integer progress;

    @Column(name = "error_message")
    private String errorMessage;
}
