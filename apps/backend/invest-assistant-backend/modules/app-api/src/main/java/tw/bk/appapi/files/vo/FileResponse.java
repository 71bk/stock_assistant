package tw.bk.appapi.files.vo;

import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tw.bk.appfiles.model.FileView;

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

    public static FileResponse from(FileView entity) {
        return FileResponse.builder()
                .fileId(entity.id() != null ? entity.id().toString() : null)
                .sha256(entity.sha256())
                .sizeBytes(entity.sizeBytes())
                .contentType(entity.contentType())
                .createdAt(entity.createdAt())
                .build();
    }
}
