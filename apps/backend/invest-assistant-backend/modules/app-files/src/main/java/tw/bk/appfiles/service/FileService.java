package tw.bk.appfiles.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tw.bk.appcommon.enums.ErrorCode;
import tw.bk.appcommon.enums.FileProvider;
import tw.bk.appcommon.exception.BusinessException;
import tw.bk.appfiles.config.FileStorageProperties;
import tw.bk.appfiles.model.FileView;
import tw.bk.appfiles.model.PresignResult;
import tw.bk.appfiles.storage.LocalObjectStorage;
import tw.bk.appfiles.storage.MinioObjectStorage;
import tw.bk.appfiles.storage.ObjectStorage;
import tw.bk.appfiles.storage.S3ObjectStorage;
import tw.bk.apppersistence.entity.FileEntity;
import tw.bk.apppersistence.repository.FileRepository;

/**
 * 檔案上傳/下載的協調者：負責去重、metadata（{@link FileEntity}）與 presign 流程，
 * 實際的物件 byte I/O 與 presign 委派給 {@link ObjectStorage} adapter（local/S3/MinIO）。
 */
@Slf4j
@Service
public class FileService {
    private static final String PROVIDER_LOCAL = FileProvider.LOCAL.name().toLowerCase(Locale.ROOT);
    private static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";

    private final FileRepository fileRepository;
    private final FileStorageProperties properties;
    private final Map<FileProvider, ObjectStorage> storages;

    public FileService(FileRepository fileRepository, FileStorageProperties properties) {
        this.fileRepository = fileRepository;
        this.properties = properties;
        this.storages = Map.of(
                FileProvider.LOCAL, new LocalObjectStorage(properties),
                FileProvider.S3, new S3ObjectStorage(properties),
                FileProvider.MINIO, new MinioObjectStorage(properties));
    }

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
        String bucket = provider == FileProvider.LOCAL ? null : resolveBucket(null);

        try {
            selectStorage(provider).store(tempFile, bucket, objectKey, normalizedContentType);
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
        String bucket = provider == FileProvider.LOCAL ? null : resolveBucket(file.getBucket());
        return selectStorage(provider).load(bucket, file.getObjectKey());
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
        String bucket = provider == FileProvider.LOCAL ? null : resolveBucket(file.bucket());
        return selectStorage(provider).load(bucket, file.objectKey());
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
        if (provider == FileProvider.S3 || provider == FileProvider.MINIO) {
            return selectStorage(provider)
                    .presignGet(resolveBucket(file.getBucket()), file.getObjectKey(), resolvePresignExpirySeconds());
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
        if (provider == FileProvider.S3 || provider == FileProvider.MINIO) {
            return selectStorage(provider)
                    .presignGet(resolveBucket(file.bucket()), file.objectKey(), resolvePresignExpirySeconds());
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

    private ObjectStorage selectStorage(FileProvider provider) {
        ObjectStorage storage = storages.get(provider);
        if (storage == null) {
            throw new BusinessException(ErrorCode.NOT_IMPLEMENTED, "File provider not supported");
        }
        return storage;
    }

    private PresignResult presignByProvider(FileEntity entity) {
        FileProvider provider = resolveProvider(entity);
        if (provider == FileProvider.S3 || provider == FileProvider.MINIO) {
            String bucket = resolveBucket(entity.getBucket());
            ObjectStorage.PresignedUpload upload = selectStorage(provider).presignPut(
                    bucket,
                    entity.getObjectKey(),
                    entity.getContentType(),
                    resolvePresignExpirySeconds());
            return new PresignResult(
                    entity.getId(),
                    entity.getObjectKey(),
                    upload.url(),
                    upload.method(),
                    upload.headers(),
                    false);
        }
        return new PresignResult(
                entity.getId(),
                entity.getObjectKey(),
                "/api/files",
                "POST",
                Map.of(),
                false);
    }

    private String providerValue(FileProvider provider) {
        if (provider == null) {
            return PROVIDER_LOCAL;
        }
        return provider.name().toLowerCase(Locale.ROOT);
    }

    private String localContentPath(Long fileId) {
        if (fileId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "file_id is required for local content path");
        }
        return "/api/files/" + fileId + "/content";
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
