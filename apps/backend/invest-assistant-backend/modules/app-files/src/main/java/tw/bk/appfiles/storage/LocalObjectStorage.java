package tw.bk.appfiles.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import lombok.extern.slf4j.Slf4j;
import tw.bk.appcommon.enums.ErrorCode;
import tw.bk.appcommon.exception.BusinessException;
import tw.bk.appfiles.config.FileStorageProperties;

/**
 * 本機檔案系統的 {@link ObjectStorage} 實作。
 *
 * <p>把物件存放在設定的本機目錄；presign 對 local 無意義，下載改由
 * {@code FileService} 回傳 {@code /api/files/{id}/content}。
 */
@Slf4j
public class LocalObjectStorage implements ObjectStorage {

    private final FileStorageProperties properties;

    public LocalObjectStorage(FileStorageProperties properties) {
        this.properties = properties;
    }

    @Override
    public void store(Path source, String bucket, String objectKey, String contentType) {
        Path baseDir = ensureBaseDir();
        Path finalPath = baseDir.resolve(objectKey).toAbsolutePath().normalize();
        if (!finalPath.startsWith(baseDir)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Invalid object key");
        }
        try {
            Files.move(source, finalPath, StandardCopyOption.REPLACE_EXISTING);
            log.info("Stored local file: path={}", finalPath);
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Failed to persist local file");
        }
    }

    @Override
    public byte[] load(String bucket, String objectKey) {
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

    @Override
    public PresignedUpload presignPut(String bucket, String objectKey, String contentType, int expirySeconds) {
        throw new UnsupportedOperationException("Local provider does not support presigned upload");
    }

    @Override
    public String presignGet(String bucket, String objectKey, int expirySeconds) {
        throw new UnsupportedOperationException("Local provider does not support presigned download");
    }

    private Path ensureBaseDir() {
        String path = properties.getLocalPath();
        if (path == null || path.trim().isEmpty()) {
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
}
