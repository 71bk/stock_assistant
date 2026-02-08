package tw.bk.appfiles.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tw.bk.appcommon.enums.ErrorCode;
import tw.bk.appcommon.enums.FileProvider;
import tw.bk.appcommon.exception.BusinessException;
import tw.bk.appfiles.config.FileStorageProperties;
import tw.bk.apppersistence.entity.FileEntity;
import tw.bk.apppersistence.repository.FileRepository;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.http.Method;
import java.net.URI;
import java.time.Duration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import tw.bk.appfiles.model.PresignResult;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileService {
    private static final String PROVIDER_LOCAL = FileProvider.LOCAL.name().toLowerCase(Locale.ROOT);
    private static final String PROVIDER_S3 = FileProvider.S3.name().toLowerCase(Locale.ROOT);
    private static final String PROVIDER_MINIO = FileProvider.MINIO.name().toLowerCase(Locale.ROOT);
    private static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";

    private final FileRepository fileRepository;
    private final FileStorageProperties properties;

    @Transactional
    public FileEntity upload(Long userId, String contentType, InputStream input) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED, "Unauthorized");
        }
        if (input == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "檔案不得為空");
        }

        Path baseDir = ensureBaseDir();
        Path tempFile = createTempFile(baseDir);

        long sizeBytes = 0;
        String sha256;
        try (InputStream in = input; OutputStream out = Files.newOutputStream(tempFile)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
                out.write(buffer, 0, read);
                sizeBytes += read;
            }
            if (sizeBytes <= 0) {
                deleteQuietly(tempFile);
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "檔案不得為空");
            }
            sha256 = toHex(digest.digest());
        } catch (IOException ex) {
            deleteQuietly(tempFile);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "檔案儲存失敗");
        } catch (NoSuchAlgorithmException ex) {
            deleteQuietly(tempFile);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "SHA-256 not available");
        }

        Optional<FileEntity> existing = fileRepository.findByUserIdAndSha256(userId, sha256);
        if (existing.isPresent()) {
            log.info("檔案已存在，返回既存記錄: fileId={}, sha256={}", existing.get().getId(), sha256);
            deleteQuietly(tempFile);
            return existing.get();
        }

        String objectKey = sha256;
        Path finalPath = baseDir.resolve(objectKey);
        log.info("準備將檔案移動到: {}", finalPath);
        try {
            Files.move(tempFile, finalPath, StandardCopyOption.REPLACE_EXISTING);
            log.info("檔案已成功儲存: path={}, size={}", finalPath, sizeBytes);
        } catch (IOException ex) {
            log.error("檔案移動失敗: from={}, to={}, error={}", tempFile, finalPath, ex.getMessage(), ex);
            deleteQuietly(tempFile);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "檔案儲存失敗");
        }

        FileEntity entity = new FileEntity();
        entity.setUserId(userId);
        entity.setProvider(PROVIDER_LOCAL);
        entity.setBucket(null);
        entity.setObjectKey(objectKey);
        entity.setSha256(sha256);
        entity.setSizeBytes(sizeBytes);
        entity.setContentType(isBlank(contentType) ? DEFAULT_CONTENT_TYPE : contentType.trim());

        try {
            return fileRepository.save(entity);
        } catch (DataIntegrityViolationException ex) {
            Optional<FileEntity> saved = fileRepository.findByUserIdAndSha256(userId, sha256);
            if (saved.isPresent()) {
                return saved.get();
            }
            throw new BusinessException(ErrorCode.CONFLICT, "檔案已存在");
        }
    }

    @Transactional(readOnly = true)
    public FileEntity getFile(Long userId, Long fileId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED, "Unauthorized");
        }
        return fileRepository.findByIdAndUserId(fileId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "檔案不存在"));
    }

    @Transactional(readOnly = true)
    public byte[] loadBytes(Long userId, Long fileId) {
        FileEntity file = getFile(userId, fileId);
        return loadBytes(file);
    }

    @Transactional(readOnly = true)
    public byte[] loadBytes(FileEntity file) {
        if (file == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "File not found");
        }

        String provider = isBlank(file.getProvider()) ? properties.getProvider() : file.getProvider();
        if (isBlank(provider)) {
            provider = PROVIDER_LOCAL;
        }
        provider = provider.trim().toLowerCase(Locale.ROOT);

        if (!PROVIDER_LOCAL.equals(provider)) {
            throw new BusinessException(ErrorCode.NOT_IMPLEMENTED, "File provider not supported");
        }
        if (isBlank(file.getObjectKey())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "File object key not found");
        }

        Path baseDir = ensureBaseDir();
        Path filePath = baseDir.resolve(file.getObjectKey()).toAbsolutePath().normalize();
        try {
            return Files.readAllBytes(filePath);
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Failed to read file content");
        }
    }

    @Transactional
    public PresignResult presignUpload(Long userId, String sha256, Long sizeBytes, String contentType) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED, "Unauthorized");
        }
        if (isBlank(sha256)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "sha256 is required");
        }
        if (sizeBytes == null || sizeBytes <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "size_bytes must be positive");
        }
        String normalizedContentType = isBlank(contentType) ? DEFAULT_CONTENT_TYPE : contentType.trim();

        Optional<FileEntity> existing = fileRepository.findByUserIdAndSha256(userId, sha256);
        FileEntity entity = existing.orElseGet(FileEntity::new);
        boolean needsSave = entity.getId() == null;
        if (entity.getId() == null) {
            entity.setUserId(userId);
            entity.setSha256(sha256);
        }
        if (isBlank(entity.getProvider())) {
            entity.setProvider(properties.getProvider());
            needsSave = true;
        }
        if (isBlank(entity.getBucket())) {
            entity.setBucket(properties.getBucket());
            needsSave = true;
        }
        if (isBlank(entity.getObjectKey())) {
            entity.setObjectKey(sha256);
            needsSave = true;
        }
        if (entity.getSizeBytes() == null || entity.getSizeBytes() <= 0) {
            entity.setSizeBytes(sizeBytes);
            needsSave = true;
        }
        if (isBlank(entity.getContentType())) {
            entity.setContentType(normalizedContentType);
            needsSave = true;
        }
        if (needsSave) {
            entity = fileRepository.save(entity);
        }

        PresignResult presign = presignByProvider(entity);
        return new PresignResult(entity, presign.uploadUrl(), presign.method(), presign.headers());
    }

    @Transactional(readOnly = true)
    public String presignDownloadUrl(FileEntity file) {
        if (file == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "File not found");
        }
        FileProvider provider = resolveProvider(file);
        if (provider == FileProvider.S3) {
            return presignS3Download(file);
        }
        if (provider == FileProvider.MINIO) {
            return presignMinioDownload(file);
        }
        throw new BusinessException(ErrorCode.NOT_IMPLEMENTED, "Presign download not supported for local storage");
    }

    public FileProvider resolveProvider(FileEntity file) {
        String provider = isBlank(file.getProvider()) ? properties.getProvider() : file.getProvider();
        if (isBlank(provider)) {
            provider = PROVIDER_LOCAL;
        }
        FileProvider resolved = FileProvider.from(provider);
        return resolved != null ? resolved : FileProvider.LOCAL;
    }

    private Path ensureBaseDir() {
        String path = properties.getLocalPath();
        if (isBlank(path)) {
            path = "./data/uploads";
        }
        Path baseDir = Paths.get(path).toAbsolutePath().normalize();
        try {
            Files.createDirectories(baseDir);
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "無法建立上傳目錄");
        }
        return baseDir;
    }

    private Path createTempFile(Path baseDir) {
        try {
            return Files.createTempFile(baseDir, "upload-", ".tmp");
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "檔案儲存失敗");
        }
    }

    private void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // ignore
        }
    }

    private String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private PresignResult presignByProvider(FileEntity entity) {
        String provider = properties.getProvider();
        if (isBlank(provider)) {
            provider = PROVIDER_LOCAL;
        }
        provider = provider.trim().toLowerCase(Locale.ROOT);

        if (PROVIDER_S3.equals(provider)) {
            return presignS3(entity);
        }
        if (PROVIDER_MINIO.equals(provider)) {
            return presignMinio(entity);
        }
        throw new BusinessException(ErrorCode.NOT_IMPLEMENTED, "Presign not supported for local storage");
    }

    private PresignResult presignS3(FileEntity entity) {
        String bucket = requireBucket();
        String region = isBlank(properties.getRegion()) ? "us-east-1" : properties.getRegion().trim();
        String accessKey = properties.getAccessKey();
        String secretKey = properties.getSecretKey();
        if (isBlank(accessKey) || isBlank(secretKey)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "S3 credentials are required");
        }

        AwsBasicCredentials credentials = AwsBasicCredentials.create(accessKey, secretKey);
        S3Presigner.Builder builder = S3Presigner.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(credentials));

        if (!isBlank(properties.getEndpoint())) {
            builder.endpointOverride(URI.create(properties.getEndpoint().trim()));
        }

        PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(entity.getObjectKey())
                .contentType(entity.getContentType())
                .build();

        int expirySeconds = properties.getPresignExpirySeconds() == null ? 900 : properties.getPresignExpirySeconds();
        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofSeconds(expirySeconds))
                .putObjectRequest(putRequest)
                .build();

        try (S3Presigner presigner = builder.build()) {
            PresignedPutObjectRequest presigned = presigner.presignPutObject(presignRequest);
            Map<String, String> headers = new HashMap<>();
            headers.put("Content-Type", entity.getContentType());
            return new PresignResult(entity, presigned.url().toString(), "PUT", headers);
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Failed to presign S3 upload");
        }
    }

    private PresignResult presignMinio(FileEntity entity) {
        String bucket = requireBucket();
        String endpoint = properties.getEndpoint();
        String accessKey = properties.getAccessKey();
        String secretKey = properties.getSecretKey();
        if (isBlank(endpoint)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "MinIO endpoint is required");
        }
        if (isBlank(accessKey) || isBlank(secretKey)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "MinIO credentials are required");
        }

        int expirySeconds = properties.getPresignExpirySeconds() == null ? 900 : properties.getPresignExpirySeconds();
        try {
            MinioClient client = MinioClient.builder()
                    .endpoint(endpoint.trim())
                    .credentials(accessKey, secretKey)
                    .build();

            String url = client.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.PUT)
                            .bucket(bucket)
                            .object(entity.getObjectKey())
                            .expiry(expirySeconds)
                            .build());

            Map<String, String> headers = new HashMap<>();
            headers.put("Content-Type", entity.getContentType());
            return new PresignResult(entity, url, "PUT", headers);
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Failed to presign MinIO upload");
        }
    }

    private String presignS3Download(FileEntity entity) {
        String bucket = requireBucket();
        String region = isBlank(properties.getRegion()) ? "us-east-1" : properties.getRegion().trim();
        String accessKey = properties.getAccessKey();
        String secretKey = properties.getSecretKey();
        if (isBlank(accessKey) || isBlank(secretKey)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "S3 credentials are required");
        }

        AwsBasicCredentials credentials = AwsBasicCredentials.create(accessKey, secretKey);
        S3Presigner.Builder builder = S3Presigner.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(credentials));

        if (!isBlank(properties.getEndpoint())) {
            builder.endpointOverride(URI.create(properties.getEndpoint().trim()));
        }

        GetObjectRequest getRequest = GetObjectRequest.builder()
                .bucket(bucket)
                .key(entity.getObjectKey())
                .build();

        int expirySeconds = properties.getPresignExpirySeconds() == null ? 900 : properties.getPresignExpirySeconds();
        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofSeconds(expirySeconds))
                .getObjectRequest(getRequest)
                .build();

        try (S3Presigner presigner = builder.build()) {
            PresignedGetObjectRequest presigned = presigner.presignGetObject(presignRequest);
            return presigned.url().toString();
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Failed to presign S3 download");
        }
    }

    private String presignMinioDownload(FileEntity entity) {
        String bucket = requireBucket();
        String endpoint = properties.getEndpoint();
        String accessKey = properties.getAccessKey();
        String secretKey = properties.getSecretKey();
        if (isBlank(endpoint)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "MinIO endpoint is required");
        }
        if (isBlank(accessKey) || isBlank(secretKey)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "MinIO credentials are required");
        }

        int expirySeconds = properties.getPresignExpirySeconds() == null ? 900 : properties.getPresignExpirySeconds();
        try {
            MinioClient client = MinioClient.builder()
                    .endpoint(endpoint.trim())
                    .credentials(accessKey, secretKey)
                    .build();

            return client.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(bucket)
                            .object(entity.getObjectKey())
                            .expiry(expirySeconds)
                            .build());
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Failed to presign MinIO download");
        }
    }

    private String requireBucket() {
        String bucket = properties.getBucket();
        if (isBlank(bucket)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Bucket is required");
        }
        return bucket.trim();
    }
}
