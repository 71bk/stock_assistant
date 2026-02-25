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
import tw.bk.apppersistence.entity.FileEntity;

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
        FileEntity file = new FileEntity();
        file.setProvider("s3");
        file.setObjectKey("obj-key");

        byte[] payload = "ocr-bytes".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        when(fileService.loadBytes(file)).thenReturn(payload);

        byte[] result = service.loadFileBytes(file);

        assertArrayEquals(payload, result);
        verify(fileService).loadBytes(file);
    }

    @Test
    void loadFileBytes_shouldRejectNullFile() {
        BusinessException ex = assertThrows(BusinessException.class, () -> service.loadFileBytes(null));

        assertEquals(ErrorCode.NOT_FOUND, ex.getErrorCode());
    }
}
