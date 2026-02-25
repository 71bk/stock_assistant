package tw.bk.appfiles.service;

import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.http.Method;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
import tw.bk.appcommon.enums.FileProvider;
import tw.bk.appcommon.exception.BusinessException;
import tw.bk.appfiles.config.FileStorageProperties;
import tw.bk.appfiles.model.FileView;
import tw.bk.appfiles.model.PresignResult;
import tw.bk.apppersistence.entity.FileEntity;
import tw.bk.apppersistence.repository.FileRepository;

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
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "File is required");
        }

        Path tempFile = createTempFile(ensureBaseDir());

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
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "File is empty");
            }
            sha256 = toHex(digest.digest());
        } catch (IOException ex) {
            deleteQuietly(tempFile);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Failed to read upload stream");
        } catch (NoSuchAlgorithmException ex) {
            deleteQuietly(tempFile);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "SHA-256 not available");
        }

        Optional<FileEntity> existing = fileRepository.findByUserIdAndSha256(userId, sha256);
        if (existing.isPresent()) {
            log.info("File deduplicated: fileId={}, sha256={}", existing.get().getId(), sha256);
            deleteQuietly(tempFile);
            return existing.get();
        }

        String objectKey = sha256;
        String normalizedContentType = isBlank(contentType) ? DEFAULT_CONTENT_TYPE : contentType.trim();
        FileProvider provider = resolveProvider(properties.getProvider());
        String bucket = null;

        try {
            if (provider == FileProvider.LOCAL) {
                persistLocal(tempFile, objectKey);
            } else if (provider == FileProvider.S3) {
                bucket = resolveBucket(null);
                uploadToS3(tempFile, bucket, objectKey, normalizedContentType);
            } else if (provider == FileProvider.MINIO) {
                bucket = resolveBucket(null);
                uploadToMinio(tempFile, bucket, objectKey, normalizedContentType);
            } else {
                throw new BusinessException(ErrorCode.NOT_IMPLEMENTED, "File provider not supported");
            }
        } finally {
            deleteQuietly(tempFile);
        }

        FileEntity entity = new FileEntity();
        entity.setUserId(userId);
        entity.setProvider(providerValue(provider));
        entity.setBucket(bucket);
        entity.setObjectKey(objectKey);
        entity.setSha256(sha256);
        entity.setSizeBytes(sizeBytes);
        entity.setContentType(normalizedContentType);

        try {
            return fileRepository.save(entity);
        } catch (DataIntegrityViolationException ex) {
            Optional<FileEntity> saved = fileRepository.findByUserIdAndSha256(userId, sha256);
            if (saved.isPresent()) {
                return saved.get();
            }
            throw new BusinessException(ErrorCode.CONFLICT, "File already exists");
        }
    }

    @Transactional
    public FileView uploadView(Long userId, String contentType, InputStream input) {
        return toFileView(upload(userId, contentType, input));
    }

    @Transactional(readOnly = true)
    public FileEntity getFile(Long userId, Long fileId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED, "Unauthorized");
        }
        return fileRepository.findByIdAndUserId(fileId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "File not found"));
    }

    @Transactional(readOnly = true)
    public FileView getFileView(Long userId, Long fileId) {
        return toFileView(getFile(userId, fileId));
    }

    @Transactional(readOnly = true)
    public byte[] loadBytes(Long userId, Long fileId) {
        return loadBytes(getFile(userId, fileId));
    }

    @Transactional(readOnly = true)
    public byte[] loadBytes(FileEntity file) {
        if (file == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "File not found");
        }
        if (isBlank(file.getObjectKey())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "File object key not found");
        }

        FileProvider provider = resolveProvider(file);
        if (provider == FileProvider.LOCAL) {
            return loadLocalBytes(file.getObjectKey());
        }
        if (provider == FileProvider.S3) {
            return loadS3Bytes(resolveBucket(file.getBucket()), file.getObjectKey());
        }
        if (provider == FileProvider.MINIO) {
            return loadMinioBytes(resolveBucket(file.getBucket()), file.getObjectKey());
        }
        throw new BusinessException(ErrorCode.NOT_IMPLEMENTED, "File provider not supported");
    }

    @Transactional(readOnly = true)
    public byte[] loadBytes(FileView file) {
        if (file == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "File not found");
        }
        if (isBlank(file.objectKey())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "File object key not found");
        }

        FileProvider provider = resolveProvider(file);
        if (provider == FileProvider.LOCAL) {
            return loadLocalBytes(file.objectKey());
        }
        if (provider == FileProvider.S3) {
            return loadS3Bytes(resolveBucket(file.bucket()), file.objectKey());
        }
        if (provider == FileProvider.MINIO) {
            return loadMinioBytes(resolveBucket(file.bucket()), file.objectKey());
        }
        throw new BusinessException(ErrorCode.NOT_IMPLEMENTED, "File provider not supported");
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
        boolean alreadyExists = existing.isPresent();
        FileProvider provider = existing.map(this::resolveProvider)
                .orElseGet(() -> resolveProvider(properties.getProvider()));

        if (provider == FileProvider.LOCAL) {
            return presignLocalFallback(existing, sha256, alreadyExists);
        }

        FileEntity entity = existing.orElseGet(FileEntity::new);
        boolean needsSave = false;

        String providerValue = providerValue(provider);
        if (!providerValue.equals(entity.getProvider())) {
            entity.setProvider(providerValue);
            needsSave = true;
        }
        if (entity.getId() == null) {
            entity.setUserId(userId);
            entity.setSha256(sha256);
            needsSave = true;
        }
        String bucket = resolveBucket(entity.getBucket());
        if (!bucket.equals(entity.getBucket())) {
            entity.setBucket(bucket);
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
        return new PresignResult(
                entity.getId(),
                entity.getObjectKey(),
                presign.uploadUrl(),
                presign.method(),
                presign.headers(),
                alreadyExists);
    }

    private PresignResult presignLocalFallback(Optional<FileEntity> existing, String sha256, boolean alreadyExists) {
        Long fileId = existing.map(FileEntity::getId).orElse(null);
        String objectKey = existing.map(FileEntity::getObjectKey)
                .filter(key -> !isBlank(key))
                .orElse(sha256);

        return new PresignResult(
                fileId,
                objectKey,
                "/api/files",
                "POST",
                Map.of(),
                alreadyExists);
    }

    @Transactional(readOnly = true)
    public String presignDownloadUrl(FileEntity file) {
        if (file == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "File not found");
        }
        if (isBlank(file.getObjectKey())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "File object key not found");
        }
        FileProvider provider = resolveProvider(file);
        if (provider == FileProvider.S3) {
            return presignS3Download(resolveBucket(file.getBucket()), file.getObjectKey());
        }
        if (provider == FileProvider.MINIO) {
            return presignMinioDownload(resolveBucket(file.getBucket()), file.getObjectKey());
        }
        return localContentPath(file.getId());
    }

    @Transactional(readOnly = true)
    public String presignDownloadUrl(FileView file) {
        if (file == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "File not found");
        }
        if (isBlank(file.objectKey())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "File object key not found");
        }
        FileProvider provider = resolveProvider(file);
        if (provider == FileProvider.S3) {
            return presignS3Download(resolveBucket(file.bucket()), file.objectKey());
        }
        if (provider == FileProvider.MINIO) {
            return presignMinioDownload(resolveBucket(file.bucket()), file.objectKey());
        }
        return localContentPath(file.id());
    }

    public FileProvider resolveProvider(FileEntity file) {
        String provider = file == null ? null : file.getProvider();
        if (isBlank(provider)) {
            provider = properties.getProvider();
        }
        return resolveProvider(provider);
    }

    public FileProvider resolveProvider(FileView file) {
        String provider = file == null ? null : file.provider();
        if (isBlank(provider)) {
            provider = properties.getProvider();
        }
        return resolveProvider(provider);
    }

    private FileProvider resolveProvider(String rawProvider) {
        if (isBlank(rawProvider)) {
            return FileProvider.LOCAL;
        }
        FileProvider resolved = FileProvider.from(rawProvider);
        if (resolved == null) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "Unsupported file provider: " + rawProvider.trim());
        }
        return resolved;
    }

    private String providerValue(FileProvider provider) {
        if (provider == null) {
            return PROVIDER_LOCAL;
        }
        return provider.name().toLowerCase(Locale.ROOT);
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
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Failed to create local upload directory");
        }
        return baseDir;
    }

    private Path createTempFile(Path baseDir) {
        try {
            return Files.createTempFile(baseDir, "upload-", ".tmp");
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Failed to create upload temp file");
        }
    }

    private void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // ignore cleanup failure
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
        FileProvider provider = resolveProvider(entity);
        String bucket = provider == FileProvider.LOCAL ? null : resolveBucket(entity.getBucket());

        if (provider == FileProvider.S3) {
            return presignS3(entity, bucket);
        }
        if (provider == FileProvider.MINIO) {
            return presignMinio(entity, bucket);
        }
        return new PresignResult(
                entity.getId(),
                entity.getObjectKey(),
                "/api/files",
                "POST",
                Map.of(),
                false);
    }

    private String localContentPath(Long fileId) {
        if (fileId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "file_id is required for local content path");
        }
        return "/api/files/" + fileId + "/content";
    }

    private PresignResult presignS3(FileEntity entity, String bucket) {
        String objectKey = entity.getObjectKey();
        if (isBlank(objectKey)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "object_key is required");
        }

        PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .contentType(entity.getContentType())
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofSeconds(resolvePresignExpirySeconds()))
                .putObjectRequest(putRequest)
                .build();

        try (S3Presigner presigner = buildS3Presigner()) {
            PresignedPutObjectRequest presigned = presigner.presignPutObject(presignRequest);
            Map<String, String> headers = new HashMap<>();
            headers.put("Content-Type", entity.getContentType());
            return new PresignResult(
                    entity.getId(),
                    objectKey,
                    presigned.url().toString(),
                    "PUT",
                    headers,
                    false);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Failed to presign S3 upload");
        }
    }

    private PresignResult presignMinio(FileEntity entity, String bucket) {
        String objectKey = entity.getObjectKey();
        if (isBlank(objectKey)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "object_key is required");
        }

        try {
            String url = buildMinioClient().getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.PUT)
                            .bucket(bucket)
                            .object(objectKey)
                            .expiry(resolvePresignExpirySeconds())
                            .build());

            Map<String, String> headers = new HashMap<>();
            headers.put("Content-Type", entity.getContentType());
            return new PresignResult(entity.getId(), objectKey, url, "PUT", headers, false);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Failed to presign MinIO upload");
        }
    }

    private String presignS3Download(String bucket, String objectKey) {
        if (isBlank(objectKey)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "object_key is required");
        }

        GetObjectRequest getRequest = GetObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofSeconds(resolvePresignExpirySeconds()))
                .getObjectRequest(getRequest)
                .build();

        try (S3Presigner presigner = buildS3Presigner()) {
            PresignedGetObjectRequest presigned = presigner.presignGetObject(presignRequest);
            return presigned.url().toString();
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Failed to presign S3 download");
        }
    }

    private String presignMinioDownload(String bucket, String objectKey) {
        if (isBlank(objectKey)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "object_key is required");
        }

        try {
            return buildMinioClient().getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(bucket)
                            .object(objectKey)
                            .expiry(resolvePresignExpirySeconds())
                            .build());
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Failed to presign MinIO download");
        }
    }

    private int resolvePresignExpirySeconds() {
        Integer expiry = properties.getPresignExpirySeconds();
        return expiry == null ? 900 : Math.max(1, expiry);
    }

    private String resolveBucket(String bucketInFile) {
        String bucket = isBlank(bucketInFile) ? properties.getBucket() : bucketInFile;
        if (isBlank(bucket)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Bucket is required");
        }
        return bucket.trim();
    }

    private String resolveRegion() {
        return isBlank(properties.getRegion()) ? "us-east-1" : properties.getRegion().trim();
    }

    private AwsBasicCredentials resolveAwsCredentials() {
        String accessKey = properties.getAccessKey();
        String secretKey = properties.getSecretKey();
        if (isBlank(accessKey) || isBlank(secretKey)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "S3 credentials are required");
        }
        return AwsBasicCredentials.create(accessKey.trim(), secretKey.trim());
    }

    private S3Client buildS3Client() {
        AwsBasicCredentials credentials = resolveAwsCredentials();
        var builder = S3Client.builder()
                .region(Region.of(resolveRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(credentials));

        if (!isBlank(properties.getEndpoint())) {
            builder.endpointOverride(URI.create(properties.getEndpoint().trim()));
        }
        return builder.build();
    }

    private S3Presigner buildS3Presigner() {
        AwsBasicCredentials credentials = resolveAwsCredentials();
        S3Presigner.Builder builder = S3Presigner.builder()
                .region(Region.of(resolveRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(credentials));

        if (!isBlank(properties.getEndpoint())) {
            builder.endpointOverride(URI.create(properties.getEndpoint().trim()));
        }
        return builder.build();
    }

    private MinioClient buildMinioClient() {
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

    private void persistLocal(Path tempFile, String objectKey) {
        Path baseDir = ensureBaseDir();
        Path finalPath = baseDir.resolve(objectKey).toAbsolutePath().normalize();
        if (!finalPath.startsWith(baseDir)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Invalid object key");
        }

        try {
            Files.move(tempFile, finalPath, StandardCopyOption.REPLACE_EXISTING);
            log.info("Stored local file: path={}", finalPath);
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Failed to persist local file");
        }
    }

    private void uploadToS3(Path sourceFile, String bucket, String objectKey, String contentType) {
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .contentType(contentType)
                .build();

        try (S3Client client = buildS3Client()) {
            client.putObject(request, RequestBody.fromFile(sourceFile));
            log.info("Stored S3 object: bucket={}, objectKey={}", bucket, objectKey);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Failed to upload file to S3");
        }
    }

    private void uploadToMinio(Path sourceFile, String bucket, String objectKey, String contentType) {
        long size;
        try {
            size = Files.size(sourceFile);
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Failed to read upload temp file");
        }

        try (InputStream in = Files.newInputStream(sourceFile)) {
            buildMinioClient().putObject(
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

    private byte[] loadLocalBytes(String objectKey) {
        Path baseDir = ensureBaseDir();
        Path filePath = baseDir.resolve(objectKey).toAbsolutePath().normalize();
        if (!filePath.startsWith(baseDir)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Invalid object key");
        }
        try {
            return Files.readAllBytes(filePath);
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Failed to read local file content");
        }
    }

    private byte[] loadS3Bytes(String bucket, String objectKey) {
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .build();
        try (S3Client client = buildS3Client()) {
            return client.getObjectAsBytes(request).asByteArray();
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Failed to read file from S3");
        }
    }

    private byte[] loadMinioBytes(String bucket, String objectKey) {
        try (InputStream in = buildMinioClient().getObject(
                GetObjectArgs.builder().bucket(bucket).object(objectKey).build())) {
            return in.readAllBytes();
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Failed to read file from MinIO");
        }
    }

    private FileView toFileView(FileEntity entity) {
        return new FileView(
                entity.getId(),
                entity.getProvider(),
                entity.getBucket(),
                entity.getObjectKey(),
                entity.getSha256(),
                entity.getSizeBytes(),
                entity.getContentType(),
                entity.getCreatedAt());
    }
}
