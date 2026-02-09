package tw.bk.appapi.ocr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tw.bk.appapi.ocr.vo.OcrJobResponse;
import tw.bk.appcommon.enums.OcrJobStatus;
import tw.bk.appcommon.result.Result;
import tw.bk.appcommon.security.CurrentUserProvider;
import tw.bk.appocr.model.OcrJobView;
import tw.bk.appocr.service.OcrService;

@ExtendWith(MockitoExtension.class)
class OcrControllerTest {

    @Mock
    private OcrService ocrService;

    @Mock
    private CurrentUserProvider currentUserProvider;

    @Mock
    private ObjectMapper objectMapper;

    private OcrController controller;

    @BeforeEach
    void setUp() {
        controller = new OcrController(ocrService, currentUserProvider, objectMapper);
    }

    @Test
    void retry_shouldCallServiceWithForceTrue() {
        OcrJobView job = new OcrJobView(123L, 456L, OcrJobStatus.QUEUED, 0, null);

        when(currentUserProvider.getUserId()).thenReturn(Optional.of(99L));
        when(ocrService.retryJob(99L, 123L, true)).thenReturn(job);

        Result<OcrJobResponse> result = controller.retry("123", true);

        assertTrue(result.isSuccess());
        assertNotNull(result.getData());
        assertEquals("123", result.getData().getJobId());
        assertEquals("456", result.getData().getStatementId());
        assertEquals(0, result.getData().getProgress());
        verify(ocrService).retryJob(99L, 123L, true);
    }

    @Test
    void retry_shouldDefaultForceToFalseWhenNull() {
        OcrJobView job = new OcrJobView(321L, 654L, OcrJobStatus.FAILED, 100, null);

        when(currentUserProvider.getUserId()).thenReturn(Optional.of(7L));
        when(ocrService.retryJob(7L, 321L, false)).thenReturn(job);

        Result<OcrJobResponse> result = controller.retry("321", null);

        assertTrue(result.isSuccess());
        assertNotNull(result.getData());
        assertEquals("321", result.getData().getJobId());
        assertEquals("654", result.getData().getStatementId());
        verify(ocrService).retryJob(7L, 321L, false);
    }
}
