package tw.bk.appfiles.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tw.bk.appcommon.enums.ErrorCode;
import tw.bk.appcommon.exception.BusinessException;
import tw.bk.appfiles.config.FileStorageProperties;
import tw.bk.appfiles.model.PresignResult;
import tw.bk.apppersistence.entity.FileEntity;
import tw.bk.apppersistence.repository.FileRepository;

@ExtendWith(MockitoExtension.class)
class FileServiceTest {

    @TempDir
    Path tempDir;

    @Mock
    private FileRepository fileRepository;

    private FileStorageProperties properties;
    private FileService service;

    @BeforeEach
    void setUp() {
        properties = new FileStorageProperties();
        properties.setProvider("local");
        properties.setLocalPath(tempDir.toString());

        service = new FileService(fileRepository, properties);
    }

    @Test
    void uploadAndLoadBytes_shouldWorkWithLocalProvider() throws Exception {
        byte[] payload = "hello-file-service".getBytes(StandardCharsets.UTF_8);
        when(fileRepository.save(any(FileEntity.class))).thenAnswer(invocation -> {
            FileEntity entity = invocation.getArgument(0);
            if (entity.getId() == null) {
                entity.setId(1L);
            }
            return entity;
        });
        when(fileRepository.findByUserIdAndSha256(eq(1L), anyString())).thenReturn(Optional.empty());

        FileEntity saved = service.upload(1L, "text/plain", new ByteArrayInputStream(payload));

        assertEquals("local", saved.getProvider());
        assertEquals(payload.length, saved.getSizeBytes());
        assertTrue(Files.exists(tempDir.resolve(saved.getObjectKey())));

        byte[] loaded = service.loadBytes(saved);
        assertArrayEquals(payload, loaded);

        verify(fileRepository).findByUserIdAndSha256(1L, saved.getSha256());
    }

    @Test
    void loadBytes_shouldUseS3BranchInsteadOfNotImplemented() {
        properties.setProvider("s3");
        properties.setBucket("demo-bucket");

        FileEntity file = new FileEntity();
        file.setProvider("s3");
        file.setBucket("demo-bucket");
        file.setObjectKey("obj-key");

        BusinessException ex = assertThrows(BusinessException.class, () -> service.loadBytes(file));

        assertEquals(ErrorCode.VALIDATION_ERROR, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("S3 credentials"));
    }

    @Test
    void presignUpload_shouldReturnLocalMultipartHintWithoutSavingPlaceholder() {
        String sha = "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08";
        when(fileRepository.findByUserIdAndSha256(1L, sha)).thenReturn(Optional.empty());

        PresignResult result = service.presignUpload(1L, sha, 1024L, "application/pdf");

        assertEquals("/api/files", result.uploadUrl());
        assertEquals("POST", result.method());
        assertEquals(sha, result.objectKey());
        assertTrue(result.headers().isEmpty());
        assertFalse(result.alreadyExists());
        assertNull(result.fileId());
        verify(fileRepository, never()).save(any(FileEntity.class));
    }

    @Test
    void presignUpload_shouldReturnExistingFileIdForLocalDuplicate() {
        String sha = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
        FileEntity existing = new FileEntity();
        existing.setId(55L);
        existing.setProvider("local");
        existing.setObjectKey(sha);
        existing.setSha256(sha);
        when(fileRepository.findByUserIdAndSha256(3L, sha)).thenReturn(Optional.of(existing));

        PresignResult result = service.presignUpload(3L, sha, 100L, "text/plain");

        assertEquals(Long.valueOf(55L), result.fileId());
        assertEquals("/api/files", result.uploadUrl());
        assertTrue(result.alreadyExists());
        verify(fileRepository, never()).save(any(FileEntity.class));
    }

    @Test
    void presignDownloadUrl_shouldReturnLocalContentPathForLocalProvider() {
        FileEntity existing = new FileEntity();
        existing.setId(77L);
        existing.setProvider("local");
        existing.setObjectKey("obj-key");

        String url = service.presignDownloadUrl(existing);

        assertEquals("/api/files/77/content", url);
    }

    @Test
    void presignUpload_shouldFailFastWhenProviderIsUnsupported() {
        properties.setProvider("unknown-provider");
        String sha = "2d711642b726b04401627ca9fbac32f5da7f7ce4f120f170f6f6f8f95c4d72d3";
        when(fileRepository.findByUserIdAndSha256(1L, sha)).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> service.presignUpload(1L, sha, 10L, "text/plain"));

        assertEquals(ErrorCode.VALIDATION_ERROR, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("Unsupported file provider"));
    }
}
