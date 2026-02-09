package tw.bk.appapi.files;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import tw.bk.appapi.files.vo.FileUrlResponse;
import tw.bk.appcommon.enums.FileProvider;
import tw.bk.appcommon.result.Result;
import tw.bk.appcommon.security.CurrentUserProvider;
import tw.bk.appfiles.model.FileView;
import tw.bk.appfiles.service.FileService;

@ExtendWith(MockitoExtension.class)
class FilesControllerTest {

    @Mock
    private FileService fileService;

    @Mock
    private CurrentUserProvider currentUserProvider;

    private FilesController controller;

    @BeforeEach
    void setUp() {
        controller = new FilesController(fileService, currentUserProvider);
        ReflectionTestUtils.setField(controller, "presignExpirySeconds", 900);
    }

    @Test
    void getFileUrl_shouldReturnLocalContentUrlForLocalProvider() {
        FileView file = fileView(11L, "local", "image/jpeg");
        when(currentUserProvider.getUserId()).thenReturn(Optional.of(3L));
        when(fileService.getFileView(3L, 11L)).thenReturn(file);
        when(fileService.resolveProvider(file)).thenReturn(FileProvider.LOCAL);

        Result<FileUrlResponse> result = controller.getFileUrl("11");

        assertTrue(result.isSuccess());
        assertNotNull(result.getData());
        assertEquals("/api/files/11/content", result.getData().getUrl());
        assertNull(result.getData().getExpiresAt());
    }

    @Test
    void getFileUrl_shouldReturnPresignedUrlForS3Provider() {
        FileView file = fileView(12L, "s3", "application/pdf");
        when(currentUserProvider.getUserId()).thenReturn(Optional.of(5L));
        when(fileService.getFileView(5L, 12L)).thenReturn(file);
        when(fileService.resolveProvider(file)).thenReturn(FileProvider.S3);
        when(fileService.presignDownloadUrl(file)).thenReturn("https://example.com/presigned-url");

        OffsetDateTime before = OffsetDateTime.now().plusSeconds(899);
        Result<FileUrlResponse> result = controller.getFileUrl("12");

        assertTrue(result.isSuccess());
        assertNotNull(result.getData());
        assertEquals("https://example.com/presigned-url", result.getData().getUrl());
        assertNotNull(result.getData().getExpiresAt());
        assertTrue(result.getData().getExpiresAt().isAfter(before));
    }

    @Test
    void getContent_shouldReturnFileBytesAndContentType() {
        FileView file = fileView(21L, "local", "application/pdf");
        byte[] payload = "demo".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        when(currentUserProvider.getUserId()).thenReturn(Optional.of(7L));
        when(fileService.getFileView(7L, 21L)).thenReturn(file);
        when(fileService.loadBytes(7L, 21L)).thenReturn(payload);

        ResponseEntity<byte[]> response = controller.getContent("21");

        assertEquals(200, response.getStatusCode().value());
        assertEquals(MediaType.APPLICATION_PDF, response.getHeaders().getContentType());
        assertEquals("inline", response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION));
        assertArrayEquals(payload, response.getBody());
        verify(fileService).loadBytes(7L, 21L);
    }

    private FileView fileView(Long id, String provider, String contentType) {
        return new FileView(
                id,
                provider,
                null,
                "obj",
                "sha",
                100L,
                contentType,
                OffsetDateTime.now());
    }
}
