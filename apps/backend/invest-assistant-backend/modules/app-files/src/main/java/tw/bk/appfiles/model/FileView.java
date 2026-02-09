package tw.bk.appfiles.model;

import java.time.OffsetDateTime;

public record FileView(
        Long id,
        String provider,
        String bucket,
        String objectKey,
        String sha256,
        Long sizeBytes,
        String contentType,
        OffsetDateTime createdAt) {
}
