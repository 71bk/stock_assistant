package tw.bk.appapi.files.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tw.bk.apppersistence.entity.FileEntity;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileResponse {
    @JsonProperty("file_id")
    private String id;

    @JsonProperty("sha256")
    private String sha256;

    @JsonProperty("size_bytes")
    private Long sizeBytes;

    @JsonProperty("content_type")
    private String contentType;

    @JsonProperty("created_at")
    private OffsetDateTime createdAt;

    public static FileResponse from(FileEntity entity) {
        return FileResponse.builder()
                .id(entity.getId() != null ? entity.getId().toString() : null)
                .sha256(entity.getSha256())
                .sizeBytes(entity.getSizeBytes())
                .contentType(entity.getContentType())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
