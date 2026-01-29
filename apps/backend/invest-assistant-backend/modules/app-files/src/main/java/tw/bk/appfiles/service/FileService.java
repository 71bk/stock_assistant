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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tw.bk.appcommon.error.ErrorCode;
import tw.bk.appcommon.exception.BusinessException;
import tw.bk.appfiles.config.FileStorageProperties;
import tw.bk.apppersistence.entity.FileEntity;
import tw.bk.apppersistence.repository.FileRepository;

@Service
@RequiredArgsConstructor
public class FileService {
    private static final String PROVIDER_LOCAL = "local";
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
            deleteQuietly(tempFile);
            return existing.get();
        }

        String objectKey = sha256;
        Path finalPath = baseDir.resolve(objectKey);
        try {
            Files.move(tempFile, finalPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
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
}
