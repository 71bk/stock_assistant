package tw.bk.appapi.files.vo;

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
    private String fileId;

    private String sha256;

    private Long sizeBytes;

    private String contentType;

    private OffsetDateTime createdAt;

    public static FileResponse from(FileEntity entity) {
        return FileResponse.builder()
                .fileId(entity.getId() != null ? entity.getId().toString() : null)
                .sha256(entity.getSha256())
                .sizeBytes(entity.getSizeBytes())
                .contentType(entity.getContentType())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
