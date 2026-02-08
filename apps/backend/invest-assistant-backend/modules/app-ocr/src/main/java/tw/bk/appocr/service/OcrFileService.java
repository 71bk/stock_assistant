package tw.bk.appocr.service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tw.bk.appcommon.enums.ErrorCode;
import tw.bk.appcommon.exception.BusinessException;
import tw.bk.appfiles.config.FileStorageProperties;
import tw.bk.apppersistence.entity.FileEntity;

@Slf4j
@Service
@RequiredArgsConstructor
public class OcrFileService {
    private final FileStorageProperties fileStorageProperties;

    public byte[] loadFileBytes(FileEntity file) {
        if (file == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "File not found");
        }
        if (file.getProvider() != null && !"local".equalsIgnoreCase(file.getProvider())) {
            throw new BusinessException(ErrorCode.NOT_IMPLEMENTED, "File provider not supported");
        }
        String localPath = fileStorageProperties.getLocalPath();
        if (localPath == null || localPath.trim().isEmpty()) {
            localPath = "./data/uploads";
        }
        Path baseDir = Paths.get(localPath).toAbsolutePath().normalize();
        Path path = baseDir.resolve(file.getObjectKey()).normalize();
        log.info("OCR file path: baseDir={}, objectKey={}, fullPath={}", baseDir, file.getObjectKey(), path);

        if (!Files.exists(path)) {
            log.error("File not found: {}", path);
            throw new BusinessException(ErrorCode.NOT_FOUND, "File not found: " + path);
        }

        try {
            return Files.readAllBytes(path);
        } catch (Exception ex) {
            log.error("Failed to read file: path={}, error={}", path, ex.getMessage(), ex);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Failed to read file: " + path);
        }
    }
}
