package tw.bk.apppersistence.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "rag_documents", schema = "vector")
public class RagDocumentEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "source_type", nullable = false)
    private String sourceType;

    @Column(name = "source_id")
    private String sourceId;

    @Column(name = "title")
    private String title;

    @Column(name = "meta")
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> meta;

    @CreationTimestamp
    @Column(name = "created_at")
    private Instant createdAt;
}
