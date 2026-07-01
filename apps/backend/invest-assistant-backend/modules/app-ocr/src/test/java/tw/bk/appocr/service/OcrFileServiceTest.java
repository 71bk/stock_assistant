package tw.bk.appocr.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tw.bk.appcommon.enums.ErrorCode;
import tw.bk.appcommon.exception.BusinessException;
import tw.bk.appfiles.service.FileService;

@ExtendWith(MockitoExtension.class)
class OcrFileServiceTest {

    @Mock
    private FileService fileService;

    private OcrFileService service;

    @BeforeEach
    void setUp() {
        service = new OcrFileService(fileService);
    }

    @Test
    void loadFileBytes_shouldDelegateToFileService() {
        byte[] payload = "ocr-bytes".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        when(fileService.loadBytes(7L, 11L)).thenReturn(payload);

        byte[] result = service.loadFileBytes(7L, 11L);

        assertArrayEquals(payload, result);
        verify(fileService).loadBytes(7L, 11L);
    }

    @Test
    void loadFileBytes_shouldRejectNullFile() {
        BusinessException ex = assertThrows(BusinessException.class, () -> service.loadFileBytes(7L, null));

        assertEquals(ErrorCode.NOT_FOUND, ex.getErrorCode());
    }
}
