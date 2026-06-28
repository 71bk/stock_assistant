package tw.bk.appfiles.storage;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import tw.bk.appcommon.enums.ErrorCode;
import tw.bk.appcommon.exception.BusinessException;
import tw.bk.appfiles.config.FileStorageProperties;

/**
 * AWS S3（或相容 endpoint）的 {@link ObjectStorage} 實作。
 */
@Slf4j
public class S3ObjectStorage implements ObjectStorage {

    private final FileStorageProperties properties;

    public S3ObjectStorage(FileStorageProperties properties) {
        this.properties = properties;
    }

    @Override
    public void store(Path source, String bucket, String objectKey, String contentType) {
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .contentType(contentType)
                .build();
        try (S3Client client = buildClient()) {
            client.putObject(request, RequestBody.fromFile(source));
            log.info("Stored S3 object: bucket={}, objectKey={}", bucket, objectKey);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Failed to upload file to S3");
        }
    }

    @Override
    public byte[] load(String bucket, String objectKey) {
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .build();
        try (S3Client client = buildClient()) {
            return client.getObjectAsBytes(request).asByteArray();
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Failed to read file from S3");
        }
    }

    @Override
    public PresignedUpload presignPut(String bucket, String objectKey, String contentType, int expirySeconds) {
        if (isBlank(objectKey)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "object_key is required");
        }
        PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .contentType(contentType)
                .build();
        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofSeconds(expirySeconds))
                .putObjectRequest(putRequest)
                .build();
        try (S3Presigner presigner = buildPresigner()) {
            PresignedPutObjectRequest presigned = presigner.presignPutObject(presignRequest);
            Map<String, String> headers = new HashMap<>();
            headers.put("Content-Type", contentType);
            return new PresignedUpload(presigned.url().toString(), "PUT", headers);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Failed to presign S3 upload");
        }
    }

    @Override
    public String presignGet(String bucket, String objectKey, int expirySeconds) {
        if (isBlank(objectKey)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "object_key is required");
        }
        GetObjectRequest getRequest = GetObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .build();
        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofSeconds(expirySeconds))
                .getObjectRequest(getRequest)
                .build();
        try (S3Presigner presigner = buildPresigner()) {
            PresignedGetObjectRequest presigned = presigner.presignGetObject(presignRequest);
            return presigned.url().toString();
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Failed to presign S3 download");
        }
    }

    private S3Client buildClient() {
        AwsBasicCredentials credentials = resolveCredentials();
        var builder = S3Client.builder()
                .region(Region.of(resolveRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(credentials));
        if (!isBlank(properties.getEndpoint())) {
            builder.endpointOverride(URI.create(properties.getEndpoint().trim()));
        }
        return builder.build();
    }

    private S3Presigner buildPresigner() {
        AwsBasicCredentials credentials = resolveCredentials();
        S3Presigner.Builder builder = S3Presigner.builder()
                .region(Region.of(resolveRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(credentials));
        if (!isBlank(properties.getEndpoint())) {
            builder.endpointOverride(URI.create(properties.getEndpoint().trim()));
        }
        return builder.build();
    }

    private AwsBasicCredentials resolveCredentials() {
        String accessKey = properties.getAccessKey();
        String secretKey = properties.getSecretKey();
        if (isBlank(accessKey) || isBlank(secretKey)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "S3 credentials are required");
        }
        return AwsBasicCredentials.create(accessKey.trim(), secretKey.trim());
    }

    private String resolveRegion() {
        return isBlank(properties.getRegion()) ? "us-east-1" : properties.getRegion().trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
