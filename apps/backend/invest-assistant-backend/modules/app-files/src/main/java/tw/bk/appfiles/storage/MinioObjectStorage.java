package tw.bk.appfiles.storage;

import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.http.Method;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import tw.bk.appcommon.enums.ErrorCode;
import tw.bk.appcommon.exception.BusinessException;
import tw.bk.appfiles.config.FileStorageProperties;

/**
 * MinIO 的 {@link ObjectStorage} 實作。
 */
@Slf4j
public class MinioObjectStorage implements ObjectStorage {

    private final FileStorageProperties properties;

    public MinioObjectStorage(FileStorageProperties properties) {
        this.properties = properties;
    }

    @Override
    public void store(Path source, String bucket, String objectKey, String contentType) {
        long size;
        try {
            size = Files.size(source);
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Failed to read upload temp file");
        }
        try (InputStream in = Files.newInputStream(source)) {
            buildClient().putObject(
                    PutObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectKey)
                            .stream(in, size, -1)
                            .contentType(contentType)
                            .build());
            log.info("Stored MinIO object: bucket={}, objectKey={}", bucket, objectKey);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Failed to upload file to MinIO");
        }
    }

    @Override
    public byte[] load(String bucket, String objectKey) {
        try (InputStream in = buildClient().getObject(
                GetObjectArgs.builder().bucket(bucket).object(objectKey).build())) {
            return in.readAllBytes();
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Failed to read file from MinIO");
        }
    }

    @Override
    public PresignedUpload presignPut(String bucket, String objectKey, String contentType, int expirySeconds) {
        if (isBlank(objectKey)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "object_key is required");
        }
        try {
            String url = buildClient().getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.PUT)
                            .bucket(bucket)
                            .object(objectKey)
                            .expiry(expirySeconds)
                            .build());
            Map<String, String> headers = new HashMap<>();
            headers.put("Content-Type", contentType);
            return new PresignedUpload(url, "PUT", headers);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Failed to presign MinIO upload");
        }
    }

    @Override
    public String presignGet(String bucket, String objectKey, int expirySeconds) {
        if (isBlank(objectKey)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "object_key is required");
        }
        try {
            return buildClient().getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(bucket)
                            .object(objectKey)
                            .expiry(expirySeconds)
                            .build());
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Failed to presign MinIO download");
        }
    }

    private MinioClient buildClient() {
        String endpoint = properties.getEndpoint();
        if (isBlank(endpoint)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "MinIO endpoint is required");
        }
        String accessKey = properties.getAccessKey();
        String secretKey = properties.getSecretKey();
        if (isBlank(accessKey) || isBlank(secretKey)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "MinIO credentials are required");
        }
        return MinioClient.builder()
                .endpoint(endpoint.trim())
                .credentials(accessKey.trim(), secretKey.trim())
                .build();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
